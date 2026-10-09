param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/env.ps1"
Push-Location $projectRoot
try {
  docker compose -p minimall-docker --env-file deploy/.env -f deploy/compose.yml stop
  if($LASTEXITCODE -ne 0){throw '停止项目容器失败'}
  & "$PSScriptRoot/stop-local.ps1"
  & "$PSScriptRoot/start-infra.ps1"
  if($LASTEXITCODE -ne 0){throw '启动依赖失败'}
  if(-not $SkipBuild) {
    Push-Location backend
    try {mvn -B -ntp -DskipTests package; if($LASTEXITCODE -ne 0){throw 'Java打包失败'}} finally {Pop-Location}
    Push-Location frontend
    try {npm ci; if($LASTEXITCODE -ne 0){throw '前端依赖安装失败'}} finally {Pop-Location}
  }
  New-Item -ItemType Directory runtime -Force | Out-Null
  $backend=Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList @('-Duser.timezone=UTC','-jar','backend/target/minimall-app.jar','--spring.profiles.active=local') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$projectRoot/runtime/backend.log" -RedirectStandardError "$projectRoot/runtime/backend-error.log"
  $backend.Id | Set-Content runtime/backend.pid
  $frontend=Start-Process -FilePath 'F:/app/node-minimall/node-v24.21.0-win-x64/node.exe' -ArgumentList @("`"$projectRoot/frontend/node_modules/vite/bin/vite.js`"",'--host','127.0.0.1') -WorkingDirectory "$projectRoot/frontend" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$projectRoot/runtime/frontend.log" -RedirectStandardError "$projectRoot/runtime/frontend-error.log"
  $frontend.Id | Set-Content runtime/frontend.pid
  $ready=$false
  for($i=0;$i -lt 120;$i++) {
    try {$health=Invoke-RestMethod http://127.0.0.1:8080/actuator/health -TimeoutSec 2; if($health.status -eq 'UP'){$ready=$true;break}} catch {}
    Start-Sleep -Seconds 1
  }
  if(-not $ready){throw '后端启动超时，请查看 runtime 日志'}
  if($frontend.HasExited){throw '前端进程已退出，请检查端口5173和 runtime/frontend-error.log'}
  Invoke-WebRequest http://127.0.0.1:5173 -UseBasicParsing -TimeoutSec 10 | Out-Null
  Write-Host '本地启动成功：http://127.0.0.1:5173；API：http://127.0.0.1:8080'
} finally {Pop-Location}
