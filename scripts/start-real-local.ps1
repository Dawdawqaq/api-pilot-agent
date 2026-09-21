param(
    [Parameter(Mandatory = $true)][string]$KeyFile,
    [int]$Port = 18081,
    [switch]$WithKnowledge
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'target/dochelper-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw '请先执行 mvn package 构建应用' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "端口 $Port 已被占用，请指定其他端口"
}
$keyText = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $KeyFile))
$keyMatch = [regex]::Match($keyText, 'sk-[A-Za-z0-9_-]+')
if (-not $keyMatch.Success) { throw '密钥文件中未找到有效格式的密钥' }
$previous = @{}
$names = @('SPRING_PROFILES_ACTIVE', 'AI_BASE_URL', 'AI_CHAT_MODEL', 'AI_API_KEY', 'SECRET_STORE_MASTER_KEY')
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
try {
    $env:SPRING_PROFILES_ACTIVE = if ($WithKnowledge) { 'local,deepseek' } else { 'local,deepseek,lightweight' }
    $env:AI_BASE_URL = 'https://api.deepseek.com'
    $env:AI_CHAT_MODEL = 'deepseek-flash'
    $env:AI_API_KEY = $keyMatch.Value
    if (-not $env:SECRET_STORE_MASTER_KEY) {
        if (-not $IsWindows) { throw '非 Windows 环境请显式配置稳定的 SECRET_STORE_MASTER_KEY' }
        $secretDirectory = Join-Path $projectRoot '.local-notes'
        $protectedKeyPath = Join-Path $secretDirectory 'secret-store-master-key.dpapi'
        New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
        if (-not (Test-Path -LiteralPath $protectedKeyPath)) {
            $generatedKey = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
            $protectedKey = ConvertFrom-SecureString (ConvertTo-SecureString $generatedKey -AsPlainText -Force)
            # 排他创建，禁止并发启动时覆盖另一进程刚刚保存的主密钥。
            $keyStream = [System.IO.File]::Open($protectedKeyPath, [System.IO.FileMode]::CreateNew)
            try {
                $bytes = [System.Text.Encoding]::UTF8.GetBytes($protectedKey)
                $keyStream.Write($bytes, 0, $bytes.Length)
            } finally {
                $keyStream.Dispose()
                $generatedKey = $null
            }
        }
        $secureKey = ConvertTo-SecureString ([System.IO.File]::ReadAllText($protectedKeyPath))
        $env:SECRET_STORE_MASTER_KEY = [System.Net.NetworkCredential]::new('', $secureKey).Password
        Write-Output '已加载 Windows 当前用户加密保存的本地主密钥；保存在 Git 忽略的 .local-notes 目录。'
    }
    $outputPath = Join-Path $projectRoot 'output'
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList @(
        '-jar', ('"' + $jarPath + '"'), "--server.port=$Port", '--server.address=127.0.0.1'
    ) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $outputPath 'app.log') `
        -RedirectStandardError (Join-Path $outputPath 'app-error.log')
    $process.Id | Set-Content (Join-Path $outputPath 'app.pid')
    Write-Output "应用进程已启动：PID=$($process.Id)，地址=http://127.0.0.1:$Port；就绪状态请查看 output/app.log。"
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    $keyText = $null
    $keyMatch = $null
}
