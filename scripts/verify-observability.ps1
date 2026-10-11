# Read-only local monitoring smoke test; run from the repository root.
# Requires a running SentinelPay Docker Compose stack; never prints secrets.
param([string]$PrometheusUrl = "http://localhost:9090")
$ErrorActionPreference = 'Stop'
$jobs = @('payment-platform','payment-mcp-server','ai-ops-agent')
Write-Host "SentinelPay observability smoke verification"
try {
    $uri = "$PrometheusUrl/api/v1/query?query=$([uri]::EscapeDataString('up{job=~"payment-platform|payment-mcp-server|ai-ops-agent"}'))"
    $response = Invoke-RestMethod -Uri $uri -TimeoutSec 10
    if ($response.status -ne 'success') { throw 'Prometheus query did not succeed' }
    $results = @($response.data.result)
    foreach ($job in $jobs) {
        $target = @($results | Where-Object { $_.metric.job -eq $job })
        if ($target.Count -ne 1) { throw "Expected one Prometheus scrape target for $job, found $($target.Count)" }
        $status = [string]$target[0].value[1]
        Write-Host "$job up=$status"
        if ($status -ne '1') { throw "Prometheus reports target $job is down" }
    }
    Write-Host 'PASS: all three application metric scrape targets are up.'
    exit 0
} catch {
    Write-Error "FAIL: observability smoke check: $($_.Exception.Message)"
    exit 1
}
