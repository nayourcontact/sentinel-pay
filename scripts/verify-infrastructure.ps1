# Read-only Docker Compose infrastructure checks; no containers/resources are created.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI not found. Install/start Docker Desktop and retry.'
    }

    & docker compose version
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose plugin is unavailable.' }

    $checks = @(
        @{ Name = 'Development Compose'; Args = @('-f', 'docker-compose.yml') },
        @{ Name = 'Isolated performance Compose'; Args = @('-p', 'sentinelpay-perf', '-f', 'docker-compose.performance.yml') }
    )

    foreach ($check in $checks) {
        Write-Host "Validating $($check.Name)..."
        & docker compose @($check.Args) config --quiet
        if ($LASTEXITCODE -ne 0) { throw "Invalid configuration: $($check.Name)" }
    }

    $perfFile = Get-Content -Raw 'docker-compose.performance.yml'
    if ($perfFile -notmatch '127\.0\.0\.1:18085:8080') {
        throw 'Performance API must be published only on 127.0.0.1:18085.'
    }
    if ($perfFile -match '(?m)^\s*perf-db:\s*\r?\n(?:.*\r?\n)*?\s*ports:') {
        Write-Warning 'Review any added PostgreSQL port publishing in the performance environment.'
    }
    Write-Host 'PASS: Compose syntax and isolated performance API port verified.' -ForegroundColor Green
    Write-Host 'Note: this is static validation; it does not prove health, security or deployment success.'
} finally {
    Pop-Location
}
