param([switch]$WithInfrastructure)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
foreach($service in @('backend','frontend')) {
  $pidFile=Join-Path $projectRoot "runtime/$service.pid"
  if(Test-Path -LiteralPath $pidFile) {
    $processId=[int](Get-Content -LiteralPath $pidFile)
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    $owned=$process -and (($service -eq 'backend' -and $process.CommandLine -like '*minimall-app.jar*') -or ($service -eq 'frontend' -and $process.CommandLine -like '*限量商品商城与订单系统*' -and $process.CommandLine -like '*vite*'))
    if($owned){Stop-Process -Id $processId -Force; Write-Host "已停止项目 $service 进程 $processId"}
  }
}
if($WithInfrastructure) {
  Push-Location $projectRoot
  try {docker compose -p minimall-local --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.local.yml stop; if($LASTEXITCODE -ne 0){throw '停止本项目依赖失败'}} finally {Pop-Location}
}
