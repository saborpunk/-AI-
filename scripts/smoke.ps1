param(
    [string]$JavaBaseUrl = 'http://127.0.0.1:8080',
    [string]$AiBaseUrl = 'http://127.0.0.1:8000'
)
$ErrorActionPreference = 'Stop'
$javaHealth = Invoke-RestMethod "$JavaBaseUrl/actuator/health" -TimeoutSec 5
if ($javaHealth.status -ne 'UP') { throw 'Java health check failed' }
$aiHealth = Invoke-RestMethod "$AiBaseUrl/health" -TimeoutSec 5
if ($aiHealth.status -ne 'UP' -or $aiHealth.mode -ne 'mock' -or $aiHealth.service -ne 'seed-ai') {
    throw 'Python health contract mismatch'
}
$schema = Invoke-RestMethod "$AiBaseUrl/openapi.json" -TimeoutSec 5
if (-not $schema.paths.'/health') { throw 'Python OpenAPI health path missing' }
Write-Output 'PASS: Java health, Python mock health, Python OpenAPI'
