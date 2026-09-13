param([string]$JavaBaseUrl = 'http://127.0.0.1:8080', [switch]$ExpectUnavailable)
$ErrorActionPreference = 'Stop'
if (-not $env:SEED_TEST_TOKEN) { throw 'Set SEED_TEST_TOKEN to a merchant JWT first.' }
$headers = @{Authorization = 'Bearer ' + $env:SEED_TEST_TOKEN}
if ($ExpectUnavailable) {
    try {
        Invoke-RestMethod -Headers $headers "$JavaBaseUrl/api/v1/germination-drafts" -Method Post -ContentType 'application/json' -Body '{"question":"demo"}' -TimeoutSec 10 | Out-Null
        throw 'Expected unavailable response'
    } catch {
        if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 503) { throw }
        $errorJson = $_.ErrorDetails.Message
        if (-not $errorJson) {
            $reader = [IO.StreamReader]::new($_.Exception.Response.GetResponseStream(), [Text.Encoding]::UTF8)
            try { $errorJson = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
        $errorBody = $errorJson | ConvertFrom-Json
        if ($errorBody.code -ne 'AI_UNAVAILABLE' -or -not $errorBody.requestId) { throw 'Error contract mismatch' }
    }
    Write-Output 'PASS: stopped Python produces HTTP 503 / AI_UNAVAILABLE'
    exit 0
}
# Unicode escapes keep this script readable by Windows PowerShell without a BOM.
$question = '\u8fd9\u4e2a\u79cd\u5b50\u53d1\u82bd\u7387\u591a\u9ad8\uff1f'
foreach ($withBatch in @($false, $true)) {
    $body = '{"question":"' + $question + '"' + $(if ($withBatch) { ',"batchCode":"DEMO-001"' } else { '' }) + '}'
    $result = Invoke-RestMethod -Headers $headers "$JavaBaseUrl/api/v1/germination-drafts" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 10
    if ($result.data.mode -ne 'mock' -or $result.data.needsHumanReview -ne $true -or
        -not $result.data.answerDraft -or $result.requestId -ne $result.data.requestId -or
        $result.data.missingFields -notcontains 'batchEvidence') { throw 'Draft contract mismatch' }
    if (-not $withBatch -and $result.data.missingFields -notcontains 'batchCode') { throw 'Missing batch not reported' }
    if ($withBatch -and ($result.data.answerDraft -notlike '*DEMO-001*' -or $result.data.missingFields -contains 'batchCode')) { throw 'Batch context lost' }
}
try {
    Invoke-RestMethod -Headers $headers "$JavaBaseUrl/api/v1/germination-drafts" -Method Post -ContentType 'application/json' -Body '{"question":" "}' -TimeoutSec 10 | Out-Null
    throw 'Blank input unexpectedly accepted'
} catch {
    if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 400) { throw }
}
Write-Output 'PASS: Java -> Python -> Java, with/without batch, invalid input rejected'
