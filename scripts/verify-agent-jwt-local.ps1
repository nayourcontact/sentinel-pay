$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$creds = Get-Content (Join-Path $root ".local\keycloak\credentials.json") -Raw | ConvertFrom-Json
$tokenUrl = "http://127.0.0.1:18088/realms/sentinelpay/protocol/openid-connect/token"
$agentUrl = "http://127.0.0.1:18081"

function TokenFor([string]$name) {
    $token = Invoke-RestMethod -Method Post -Uri $tokenUrl -ContentType "application/x-www-form-urlencoded" -Body @{
        grant_type = "password"
        client_id = $creds.clientId
        username = $name
        password = $creds.users.$name
    }
    return $token.access_token
}

function HttpStatus([string]$url, [string]$token = "", [string]$apiKey = "") {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    if ($apiKey) { $headers["X-API-Key"] = $apiKey }
    try {
        return [int](Invoke-WebRequest -Uri $url -Method GET -Headers $headers -UseBasicParsing).StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

$agentUser = TokenFor "agent-user"
$opsReader = TokenFor "ops-reader"
$noRole = TokenFor "no-role"

$search = "$agentUrl/api/v1/knowledge/search?q=test"
$metrics = "$agentUrl/actuator/prometheus"

$tests = @(
    @{name="Unauthenticated search"; expected=401; actual=(HttpStatus $search)}
    @{name="Invalid JWT"; expected=401; actual=(HttpStatus $search "invalid-token")}
    @{name="No-role search"; expected=403; actual=(HttpStatus $search $noRole)}
    @{name="Agent-user search"; expected=200; actual=(HttpStatus $search $agentUser)}
    @{name="Ops-reader search denied"; expected=403; actual=(HttpStatus $search $opsReader)}
    @{name="Ops-reader metrics"; expected=200; actual=(HttpStatus $metrics $opsReader)}
    @{name="Agent-user metrics denied"; expected=403; actual=(HttpStatus $metrics $agentUser)}
    @{name="Invalid API-key"; expected=401; actual=(HttpStatus $search "" "invalid-test-key")}
    @{name="Legacy API-key search"; expected=200; actual=(HttpStatus $search "" "dev-secret-change-me")}
    @{name="Legacy API-key metrics denied"; expected=403; actual=(HttpStatus $metrics "" "dev-secret-change-me")}
)

$failed = 0
foreach ($t in $tests) {
    if ($t.actual -eq $t.expected) {
        Write-Host "PASS $($t.name): $($t.actual)" -ForegroundColor Green
    } else {
        Write-Host "FAIL $($t.name): expected $($t.expected), got $($t.actual)" -ForegroundColor Red
        $failed++
    }
}

if ($failed -gt 0) { throw "$failed Agent JWT/RBAC checks failed" }
Write-Host "PASS: All 10 Agent JWT/RBAC checks." -ForegroundColor Green