$ErrorActionPreference='Stop'
. "$PSScriptRoot/env.ps1"
$env:DOCKER_API_VERSION='1.44'
Push-Location (Join-Path $projectRoot 'backend')
try { & mvn -B -ntp verify; if($LASTEXITCODE -ne 0){throw '后端测试失败'} } finally { Pop-Location }
