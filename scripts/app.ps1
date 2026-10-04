param(
    [ValidateSet('start','stop','status','logs','build')][string]$Action = 'status',
    [ValidateSet('Full','Core')][string]$Mode = 'Full',
    [int]$Port = 18081,
    [switch]$Build,
    [int]$Tail = 80,
    [switch]$NoFollow
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $projectRoot 'target/dochelper-0.0.1-SNAPSHOT.jar'
$frontend = Join-Path $projectRoot 'frontend'
$distribution = Join-Path $frontend 'dist'
$stateFile = Join-Path $projectRoot '.local-notes/app-state.json'
$legacyPid = Join-Path $projectRoot 'output/app.pid'
$logDirectory = Join-Path $projectRoot 'logs'
$log = Join-Path $logDirectory 'dochelper.log'
. (Join-Path $PSScriptRoot 'local-environment.ps1')

function Build-Application {
    Push-Location $frontend
    try {
        if (-not (Test-Path -LiteralPath 'node_modules')) { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' } }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
    } finally { Pop-Location }
    Push-Location $projectRoot
    try { & mvn.cmd clean package -DskipTests -q; if ($LASTEXITCODE -ne 0) { throw '后端构建失败' } }
    finally { Pop-Location }
}

function Get-OwnedApplication {
    $applicationId = $null
    $state = $null
    if (Test-Path -LiteralPath $stateFile) {
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        $applicationId = $state.pid
    } elseif (Test-Path -LiteralPath $legacyPid) { $applicationId = (Get-Content -LiteralPath $legacyPid -Raw).Trim() }
    if (-not $applicationId) { return $null }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$applicationId)" -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    if ($process.Name -notmatch '^java(?:w)?\.exe$' -or $process.CommandLine -notlike "*$jar*") {
        throw '记录中的进程不属于当前 DocHelper，拒绝操作；请检查 .local-notes/app-state.json 和 output/app.pid'
    }
    if ($state -and $state.startedAt) {
        $started = (Get-Process -Id $process.ProcessId).StartTime.ToUniversalTime()
        $recorded = if ($state.startedAt -is [DateTime]) { $state.startedAt.ToUniversalTime() } else {
            [DateTime]::Parse($state.startedAt,[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
        }
        if ([Math]::Abs(($started - $recorded).TotalSeconds) -gt 2) {
            throw '进程编号已被复用，拒绝停止；请重新检查进程记录'
        }
    }
    return @{pid=$process.ProcessId; port=$(if ($state) { $state.port } else { $Port })}
}

switch ($Action) {
    'build' { if (Get-OwnedApplication) { throw '请先停止当前后端，再构建，避免 Windows 锁定运行中的 jar' }; Build-Application; Write-Output '当前前后端已构建，新版静态资源已打入 jar。' }
    'stop' {
        $owned = Get-OwnedApplication
        if ($owned) {
            # Windows 没有通用的 SIGTERM；只停止已校验归属的应用进程，任务恢复由后端租约机制处理。
            Stop-Process -Id $owned.pid -ErrorAction Stop
            Wait-Process -Id $owned.pid -Timeout 20 -ErrorAction SilentlyContinue
            Write-Output "已停止 DocHelper PID=$($owned.pid)。共享开发设施继续运行。"
        } else { Write-Output '当前没有本脚本记录的 DocHelper 进程。' }
    }
    'logs' {
        if (-not (Test-Path -LiteralPath $log)) { throw '尚无日志文件，请先通过 app.ps1 start 启动应用' }
        if ($NoFollow) { Get-Content -LiteralPath $log -Encoding utf8 -Tail $Tail }
        else { Write-Output '按 Ctrl+C 退出日志跟踪，应用继续运行。'; Get-Content -LiteralPath $log -Encoding utf8 -Tail $Tail -Wait }
    }
    'status' {
        $owned = Get-OwnedApplication
        $statusPort = if ($owned) { $owned.port } else { $Port }
        if ($owned) { Write-Output "DocHelper PID=$($owned.pid)，地址=http://127.0.0.1:$statusPort" }
        else { Write-Output '没有本脚本记录的应用进程；仍尝试检查指定端口。' }
        try { $health = Invoke-RestMethod "http://127.0.0.1:$statusPort/actuator/health" -TimeoutSec 5; Write-Output "健康状态：$($health.status)" }
        catch { Write-Output '健康检查不可用：应用尚未就绪、未启动或端口配置不同。' }
    }
    'start' {
        $owned = Get-OwnedApplication
        if ($owned) { Write-Output "应用已经运行：PID=$($owned.pid)，http://127.0.0.1:$($owned.port)"; return }
        if ($Port -lt 1024 -or $Port -gt 65535) { throw '端口须为 1024—65535' }
        if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "端口 $Port 被其他进程占用，请指定 -Port；脚本不会停止占用者" }
        if ($Build -or -not (Test-Path -LiteralPath $jar) -or -not (Test-Path -LiteralPath (Join-Path $distribution 'index.html'))) { Build-Application }
        New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
        Invoke-DocHelperEnvironment -ProjectRoot $projectRoot -Operation {
            $profiles = if ($Mode -eq 'Core') { 'local,stub,lightweight' } else { 'local,stub' }
            $application = Start-Process -FilePath (Get-Command java).Source -WindowStyle Hidden -PassThru -WorkingDirectory $projectRoot -ArgumentList @(
                ('-Ddochelper.log.dir="' + $logDirectory + '"'), '-jar', ('"' + $jar + '"'),
                "--server.port=$Port", '--server.address=127.0.0.1', "--spring.profiles.active=$profiles",
                '--logging.config=classpath:logback-personal.xml'
            ) -RedirectStandardOutput (Join-Path $logDirectory 'startup.log') -RedirectStandardError (Join-Path $logDirectory 'startup-error.log')
            @{pid=$application.Id; port=$Port; mode=$Mode; startedAt=$application.StartTime.ToUniversalTime().ToString('o')} |
                ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding utf8
            New-Item -ItemType Directory -Path (Split-Path -Parent $legacyPid) -Force | Out-Null
            $application.Id | Set-Content -LiteralPath $legacyPid
            $ready = $false
            for ($attempt=0; $attempt -lt 90; $attempt++) {
                if ($application.HasExited) { throw '应用启动失败，请检查 logs/dochelper.log 与 startup-error.log' }
                try { $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2; if ($health.status -eq 'UP') { $ready=$true; break } }
                catch { }
                Start-Sleep -Seconds 1
            }
            if (-not $ready) { Write-Output '应用进程已启动，健康检查尚未通过，请运行 app.ps1 status 和 app.ps1 logs。'; return }
            Write-Output "DocHelper 已就绪：http://127.0.0.1:$Port （$Mode 模式，新版前端与 API 同一端口）。"
            Write-Output '查看日志：.\scripts\app.ps1 logs；停止应用：.\scripts\app.ps1 stop'
        }
    }
}
