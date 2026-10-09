$ErrorActionPreference='Stop'
. "$PSScriptRoot/env.ps1"
Push-Location $projectRoot
try { & "$env:JAVA_HOME/bin/java.exe" examples/HelloJava.java; if($LASTEXITCODE -ne 0){throw 'Java示例运行失败'} } finally {Pop-Location}
