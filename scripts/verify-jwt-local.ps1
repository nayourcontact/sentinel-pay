param([Parameter(Mandatory=$true)][string]$PaymentId)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$creds=Get-Content (Join-Path $root '.local\keycloak\credentials.json') -Raw | ConvertFrom-Json
$tokenUrl='http://127.0.0.1:18088/realms/sentinelpay/protocol/openid-connect/token'
$paymentUrl="http://127.0.0.1:18085/api/payments/$PaymentId"
function TokenFor([string]$name) {
    $token=Invoke-RestMethod -Method Post -Uri $tokenUrl -ContentType 'application/x-www-form-urlencoded' -Body @{
        grant_type='password'; client_id=$creds.clientId; username=$name; password=$creds.users.$name
    }
    return $token.access_token
}
function HttpStatus([string]$method,[string]$url,[string]$token,[string]$body='') {
    $params=@{ Uri=$url; Method=$method; UseBasicParsing=$true; ErrorAction='Stop' }
    if ($token) { $params.Headers=@{ Authorization="Bearer $token" } }
    if ($body) { $params.Body=$body; $params.ContentType='application/json'; $params.Headers['Idempotency-Key']="local-jwt-$([guid]::NewGuid().ToString('N'))" }
    try { return [int](Invoke-WebRequest @params).StatusCode }
    catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}
$reader=TokenFor 'reader'
$operator=TokenFor 'operator'
$noRole=TokenFor 'no-role'
$tests=@(
    @{ name='Unauthenticated GET'; expected=401; actual=(HttpStatus 'GET' $paymentUrl '') },
    @{ name='Invalid token GET'; expected=401; actual=(HttpStatus 'GET' $paymentUrl 'not-a-token') },
    @{ name='No-role GET'; expected=403; actual=(HttpStatus 'GET' $paymentUrl $noRole) },
    @{ name='Reader GET'; expected=200; actual=(HttpStatus 'GET' $paymentUrl $reader) },
    @{ name='Reader POST denied'; expected=403; actual=(HttpStatus 'POST' 'http://127.0.0.1:18085/api/payments' $reader '{"amount":"1.00","currency":"HUF"}') },
    @{ name='Operator GET'; expected=200; actual=(HttpStatus 'GET' $paymentUrl $operator) }
)
$failed=0
foreach($t in $tests) {
    if($t.actual -eq $t.expected) { Write-Host "PASS $($t.name): $($t.actual)" }
    else { Write-Host "FAIL $($t.name): expected $($t.expected), got $($t.actual)"; $failed++ }
}
if($failed -gt 0) { throw "$failed JWT/RBAC checks failed" }
Write-Host 'PASS: read-only JWT/RBAC checks. POST authorization test for operator is intentionally omitted.'
