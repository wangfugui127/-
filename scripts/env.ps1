$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME='F:\app\java\jdk-21.0.12.1+1'
$env:MAVEN_HOME='F:\app\maven\apache-maven-3.9.11'
$env:Path="F:\app\node-minimall\node-v24.21.0-win-x64;$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
$env:NPM_CONFIG_CACHE='F:\app\npm-cache-minimall'
$env:PLAYWRIGHT_BROWSERS_PATH='F:\app\playwright'
Get-Content -LiteralPath (Join-Path $projectRoot 'deploy\.env') | ForEach-Object {
 if($_ -match '^([A-Z_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}
}
$env:DB_URL='jdbc:mysql://127.0.0.1:13306/minimall?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:DB_USER=$env:MYSQL_USER
$env:DB_PASSWORD=$env:MYSQL_PASSWORD
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='16379'
$env:ROCKETMQ_NAMESERVER='127.0.0.1:9876'
$env:MESSAGING_ENABLED='true'
$env:JOBS_ENABLED='true'
