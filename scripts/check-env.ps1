$ErrorActionPreference='Stop'
. "$PSScriptRoot/env.ps1"
java -version
mvn -version
node --version
npm --version
docker version --format '{{.Server.Version}}'
docker compose version
if($LASTEXITCODE -ne 0){throw 'Docker Desktop 尚未启动'}
git -C $projectRoot remote -v
Write-Host '项目环境就绪。VS Code 请打开项目根目录，而非单个 Java 文件。'
