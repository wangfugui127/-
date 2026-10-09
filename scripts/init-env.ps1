$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
function Random-Hex([int]$size) {
  $buffer=New-Object byte[] $size
  $generator=[System.Security.Cryptography.RandomNumberGenerator]::Create()
  try {$generator.GetBytes($buffer); return [BitConverter]::ToString($buffer).Replace('-','').ToLowerInvariant()} finally {$generator.Dispose()}
}
$envFile=Join-Path $projectRoot 'deploy/.env'
if(-not(Test-Path -LiteralPath $envFile)) {
  @(
    'MYSQL_DATABASE=minimall'
    'MYSQL_USER=minimall'
    "MYSQL_PASSWORD=$(Random-Hex 24)"
    "MYSQL_ROOT_PASSWORD=$(Random-Hex 24)"
    "REDIS_PASSWORD=$(Random-Hex 24)"
    "MOCK_PAYMENT_SECRET=$(Random-Hex 32)"
    "BOOTSTRAP_ADMIN_PASSWORD=MiniMall!$(Random-Hex 8)"
    'ORDER_TIMEOUT_MINUTES=10'
  ) | Set-Content -LiteralPath $envFile -Encoding ascii
}
. "$PSScriptRoot/env.ps1"
@(
 "DB_URL=$env:DB_URL"
 "DB_USER=$env:DB_USER"
 "DB_PASSWORD=$env:DB_PASSWORD"
 'REDIS_HOST=127.0.0.1'
 'REDIS_PORT=16379'
 "REDIS_PASSWORD=$env:REDIS_PASSWORD"
 'ROCKETMQ_NAMESERVER=127.0.0.1:9876'
 'MESSAGING_ENABLED=true'
 'JOBS_ENABLED=true'
 "MOCK_PAYMENT_SECRET=$env:MOCK_PAYMENT_SECRET"
 "BOOTSTRAP_ADMIN_PASSWORD=$env:BOOTSTRAP_ADMIN_PASSWORD"
 "ORDER_TIMEOUT_MINUTES=$env:ORDER_TIMEOUT_MINUTES"
) | Set-Content -LiteralPath (Join-Path $projectRoot 'deploy/local.env') -Encoding ascii
Write-Host '已保留现有 .env，并同步 VS Code 的 local.env。管理员密码请查看 deploy/.env。'
