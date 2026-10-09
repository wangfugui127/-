. "$PSScriptRoot\env.ps1"
Set-Location -LiteralPath (Join-Path $projectRoot 'backend')
& "$env:MAVEN_HOME\bin\mvn.cmd" spring-boot:run '-Dspring-boot.run.profiles=local'
exit $LASTEXITCODE
