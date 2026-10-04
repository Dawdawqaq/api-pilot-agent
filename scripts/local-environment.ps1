# 只读取 DocHelper 自己的凭据；不覆盖调用者显式配置的环境变量。
function Invoke-DocHelperEnvironment {
    param([string]$ProjectRoot, [scriptblock]$Operation)
    $names = @('DB_USERNAME','DB_PASSWORD','MINIO_ACCESS_KEY','MINIO_SECRET_KEY','SECRET_STORE_MASTER_KEY')
    $previous = @{}
    foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
    try {
        if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'dochelper' }
        if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = 'dochelper-local' }
        if (-not $env:MINIO_ACCESS_KEY) { $env:MINIO_ACCESS_KEY = 'dochelper' }
        if (-not $env:MINIO_SECRET_KEY) { $env:MINIO_SECRET_KEY = 'dochelper-local-secret' }
        $infraRoot = if ($env:DOCHELPER_DEV_INFRA) { $env:DOCHELPER_DEV_INFRA } else { Join-Path (Split-Path -Parent $ProjectRoot) 'dev-infra' }
        $infraFile = Join-Path $infraRoot '.env'
        $mapping = @{DOCHELPER_DB_PASSWORD='DB_PASSWORD'; DOCHELPER_MINIO_ACCESS_KEY='MINIO_ACCESS_KEY'; DOCHELPER_MINIO_SECRET_KEY='MINIO_SECRET_KEY'}
        if (Test-Path -LiteralPath $infraFile) {
            foreach ($line in [IO.File]::ReadLines($infraFile)) {
                if ($line -match '^\s*(DOCHELPER_DB_PASSWORD|DOCHELPER_MINIO_ACCESS_KEY|DOCHELPER_MINIO_SECRET_KEY)\s*=(.*)$') {
                    $target = $mapping[$Matches[1]]
                    if (-not $previous[$target]) { [Environment]::SetEnvironmentVariable($target,$Matches[2].Trim().Trim('"').Trim("'")) }
                }
            }
        }
        if (-not $env:SECRET_STORE_MASTER_KEY) {
            if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw '请显式配置 SECRET_STORE_MASTER_KEY' }
            $directory = Join-Path $ProjectRoot '.local-notes'
            $keyFile = Join-Path $directory 'secret-store-master-key.dpapi'
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
            if (-not (Test-Path -LiteralPath $keyFile)) {
                $bytes = New-Object byte[] 32
                $random = [Security.Cryptography.RandomNumberGenerator]::Create()
                try { $random.GetBytes($bytes) } finally { $random.Dispose() }
                $secure = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
                $stream = [IO.File]::Open($keyFile,[IO.FileMode]::CreateNew)
                try {
                    $protected = [Text.Encoding]::UTF8.GetBytes((ConvertFrom-SecureString $secure))
                    $stream.Write($protected,0,$protected.Length)
                } finally { $stream.Dispose(); [Array]::Clear($bytes,0,$bytes.Length); $secure = $null }
            }
            $secure = ConvertTo-SecureString ([IO.File]::ReadAllText($keyFile))
            $env:SECRET_STORE_MASTER_KEY = [Net.NetworkCredential]::new('',$secure).Password
        }
        & $Operation
    } finally {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name]) }
        $secure = $null
    }
}
