param([switch]$Browser,[string]$BaseUrl='http://127.0.0.1:5173')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/env.ps1"
Push-Location (Join-Path $projectRoot 'frontend')
try {
  foreach($task in @('type-check','lint')) { & npm run $task; if($LASTEXITCODE -ne 0){throw "$task 失败"} }
  & npm run test:unit -- --run
  if($LASTEXITCODE -ne 0){throw '前端单元测试失败'}
  & npm run build
  if($LASTEXITCODE -ne 0){throw '前端打包失败'}
  if($Browser){$env:BASE_URL=$BaseUrl; & npm run test:e2e; if($LASTEXITCODE -ne 0){throw '浏览器测试失败'}}
} finally {Pop-Location}
