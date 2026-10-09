. "$PSScriptRoot\env.ps1"
Set-Location -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend')
npm.cmd run dev
exit $LASTEXITCODE
