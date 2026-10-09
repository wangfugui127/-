param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/env.ps1"
$asciiRoot='F:/app/workspaces/minimall'
if(-not(Test-Path -LiteralPath $asciiRoot)) {
  New-Item -ItemType Directory (Split-Path $asciiRoot) -Force | Out-Null
  New-Item -ItemType Junction -Path $asciiRoot -Target $projectRoot | Out-Null
}
if((Get-Item -LiteralPath $asciiRoot).Target -ne $projectRoot){throw '英文入口已指向其他目录，请检查 F:/app/workspaces/minimall'}
Push-Location $asciiRoot
try {
  if(-not $SkipBuild) {
    $built=$false
    for($attempt=1;$attempt -le 3;$attempt++) {
      docker compose -p minimall-docker --env-file deploy/.env -f deploy/compose.yml build
      if($LASTEXITCODE -eq 0){$built=$true;break}
      if($attempt -lt 3){Write-Host "构建未完成，2秒后重试（$attempt/3）；已完成的层将复用缓存。"; Start-Sleep -Seconds 2}
    }
    if(-not $built){throw '镜像构建失败，请检查网络与上方具体错误'}
  }
  & "$PSScriptRoot/stop-local.ps1" -WithInfrastructure
  docker compose -p minimall-docker --env-file deploy/.env -f deploy/compose.yml up -d --wait --wait-timeout 240
  if($LASTEXITCODE -ne 0){throw '容器启动未完成，请检查 docker compose 日志'}
  Invoke-WebRequest http://127.0.0.1:8088 -UseBasicParsing -TimeoutSec 10 | Out-Null
  Write-Host 'Docker 启动成功：http://127.0.0.1:8088；API：http://127.0.0.1:8080'
} finally {Pop-Location}
