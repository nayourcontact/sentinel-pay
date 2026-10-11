param(
    [ValidateSet('read','write','race')][string]$Mode = 'read',
    [string]$PaymentId = '',
    [string]$BaseUrl = 'http://localhost:8085',
    [switch]$IsolatedDatabaseConfirmed
)
$ErrorActionPreference = 'Stop'
if ($BaseUrl -notmatch '^http://(localhost|127\.0\.0\.1)(:\d+)?$') { throw 'Only a local BASE_URL is allowed.' }
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) { throw 'k6 not found. Install with: winget install k6.k6' }
if ($Mode -eq 'read' -and $PaymentId -notmatch '^[0-9a-fA-F-]{36}$') { throw 'Read mode needs -PaymentId <test-UUID>.' }
if ($Mode -ne 'read' -and -not $IsolatedDatabaseConfirmed) { throw 'Write/race modes require -IsolatedDatabaseConfirmed. Never target the retained payments database.' }
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$results = Join-Path $root 'performance/results'
New-Item -ItemType Directory -Force -Path $results | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$env:BASE_URL = $BaseUrl
$env:MODE = $Mode
$env:PAYMENT_ID = $PaymentId
$env:PERF_ALLOW_WRITES = if ($IsolatedDatabaseConfirmed) { 'YES_ISOLATED_DATABASE' } else { '' }
$script = if ($Mode -eq 'race') { 'idempotency-race.js' } else { 'payment-load.js' }
$target = Join-Path $root "performance/$script"
$out = Join-Path $results "$Mode-$stamp.json"
try {
    & k6 run --summary-export $out $target
    if ($LASTEXITCODE -ne 0) { throw "k6 returned exit code $LASTEXITCODE. See $out" }
    Write-Host "PASS: results written to $out"
} finally {
    Remove-Item Env:PERF_ALLOW_WRITES -ErrorAction SilentlyContinue
    Remove-Item Env:PAYMENT_ID -ErrorAction SilentlyContinue
}
