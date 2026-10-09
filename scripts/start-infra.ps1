$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
docker compose -p minimall-local --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.local.yml up -d --wait --wait-timeout 240 mysql redis namesrv broker mq-init
exit $LASTEXITCODE
