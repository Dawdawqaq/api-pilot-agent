param(
    [ValidateSet('backup','verify','restore')][string]$Action = 'backup',
    [string]$BackupPath,
    [string]$Confirmation
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'local-environment.ps1')
$jar = Join-Path $projectRoot 'target/dochelper-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw '请先通过 app.ps1 build 构建备份工具' }
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
$docker = if ($dockerCommand) { $dockerCommand.Source } else { Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe' }
if (-not (Test-Path -LiteralPath $docker)) { throw '未找到 Docker，请检查 Docker Desktop 安装' }
if ($Action -ne 'verify') {
    $running = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -like "*$projectRoot*" }
    if ($running) { throw '完整备份或恢复前请停止当前 DocHelper，避免数据在备份期间变化。运行 .\scripts\app.ps1 stop' }
    if (Get-NetTCPConnection -State Listen -LocalPort 18081 -ErrorAction SilentlyContinue) { throw '18081 仍有服务运行，请确认已停止 DocHelper' }
}
if ($Action -eq 'restore') {
    if ($Confirmation -ne 'dochelper') { throw '恢复会替换 DocHelper 数据库，必须添加 -Confirmation dochelper' }
    if (-not $BackupPath) { throw '请提供 -BackupPath' }
    $keyPath = Join-Path $BackupPath 'master-key.dpapi'
    # 提前证明可以解密，避免写完数据库才发现备份属于另一台电脑或另一用户。
    $secure = ConvertTo-SecureString ([IO.File]::ReadAllText((Resolve-Path -LiteralPath $keyPath)))
    $secure = $null
}
Invoke-DocHelperEnvironment -ProjectRoot $projectRoot -Operation {
    $previous = $env:DOCHELPER_BACKUP_PROTECTED_KEY
    try {
        $env:DOCHELPER_BACKUP_PROTECTED_KEY = ConvertFrom-SecureString (ConvertTo-SecureString $env:SECRET_STORE_MASTER_KEY -AsPlainText -Force)
        $arguments = @((Join-Path $PSScriptRoot 'backup_dochelper.py'),$Action,'--root',$projectRoot,'--docker',$docker)
        if ($BackupPath) { $arguments += @('--path',(Resolve-Path -LiteralPath $BackupPath).Path) }
        if ($Confirmation) { $arguments += @('--confirmation',$Confirmation) }
        & python -X utf8 @arguments
        if ($LASTEXITCODE -ne 0) { throw '备份操作未完成；已有完整备份不受影响，请检查上述说明' }
    } finally { $env:DOCHELPER_BACKUP_PROTECTED_KEY = $previous }
}
