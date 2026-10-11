param(
  [ValidateSet('read','write','race')][string]$Mode = 'read',
  [string]$PaymentId = ''
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($Mode -eq 'read' -and $PaymentId -notmatch '^[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}$') { throw 'Read requires an existing test PaymentId UUID.' }
# Enforce isolated service existence; user-supplied flag alone is not proof of isolation.
$running = (& docker compose -p sentinelpay-perf -f (Join-Path $root 'docker-compose.performance.yml') ps --status running --services 2>$null)
if ($LASTEXITCODE -ne 0 -or $running -notcontains 'perf-payment-platform' -or $running -notcontains 'perf-db' -or $running -notcontains 'perf-kafka') { throw 'Isolated perf stack is not running. Start docker-compose.performance.yml first.' }
$results = Join-Path $root 'performance/results'
New-Item -Path $results -ItemType Directory -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$script = if ($Mode -eq 'race') { 'idempotency-race.js' } else { 'payment-load.js' }
$filename = "$Mode-$stamp.json"
$argsList = @('run', '--rm', '-v', "${root}:/scripts:ro", '-v', "${results}:/results", '-e', 'BASE_URL=http://host.docker.internal:18085', '-e', "MODE=$Mode", '-e', "PAYMENT_ID=$PaymentId", '-e', 'PERF_ALLOW_WRITES=YES_ISOLATED_DATABASE', 'grafana/k6:latest', 'run', '--summary-export', "/results/$filename", "/scripts/performance/$script")
& docker @argsList
if ($LASTEXITCODE -ne 0) { throw "k6 failed (exit=$LASTEXITCODE). Review performance/results/$filename" }
Write-Host "PASS: performance/results/$filename"
