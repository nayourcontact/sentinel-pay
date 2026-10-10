$ErrorActionPreference = 'Stop'
Write-Host 'SentinelPay - unit and Testcontainers integration tests' -ForegroundColor Cyan
$docker = docker info --format '{{.ServerVersion}}' 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is not available. Start Docker Desktop and retry.' }
Write-Host "Docker Engine: $docker"
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) { throw 'Maven (mvn) is not on PATH.' }
# verify triggers Surefire unit tests, then Failsafe *IT integration tests.
& mvn -pl payment-platform -am -DtrimStackTrace=false verify
if ($LASTEXITCODE -ne 0) { throw 'Maven verify FAILED. See Surefire/Failsafe reports in payment-platform/target.' }
Write-Host 'All discovered unit and integration tests passed.' -ForegroundColor Green
