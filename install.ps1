$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $RootDir "deploy/lan/install.ps1") @args
exit $LASTEXITCODE
