param(
    [ValidateSet('category','flow','readback')][string]$Mode = 'flow',
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.UseProxy = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(15)
$statePath = Join-Path (Split-Path $PSScriptRoot -Parent) '.tools/business-check.json'
function Check($condition, [string]$message) { if (-not $condition) { throw $message } }
function Call([string]$method, [string]$path, [int]$expected, $body = $null) {
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($method), ($BaseUrl + '/api/v1' + $path))
    if ($null -ne $body) {
        $request.Content = New-Object System.Net.Http.StringContent(($body | ConvertTo-Json -Depth 10), [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            Check ([int]$response.StatusCode -eq $expected) "$method $path returned $([int]$response.StatusCode), expected $expected"
            Check ($response.Headers.Contains('X-Request-Id')) 'Missing request ID'
            if ($expected -eq 201) { Check ($null -ne $response.Headers.Location) 'Missing Location' }
            $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ($text) { return ($text | ConvertFrom-Json) }
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
try {
    if ($Mode -eq 'readback') {
        $saved = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($item in $saved) {
            $actual = Call 'GET' $item.path 200
            Check (($actual | ConvertTo-Json -Depth 10 -Compress) -eq ($item.record | ConvertTo-Json -Depth 10 -Compress)) 'Record changed across restart'
        }
        Write-Output 'PASS: all saved business records survive restart'
        exit 0
    }
    $name = 'SYNTHETIC-' + [guid]::NewGuid().ToString('N')
    $category = Call 'POST' '/article-categories' 201 @{name=$name;description='Synthetic acceptance record'}
    $categoryPath = '/article-categories/' + $category.id
    Check ($category.status -eq 'ENABLED' -and $category.version -eq 0) 'Incorrect category defaults'
    $category = Call 'PUT' $categoryPath 200 @{name=$name;description='Updated synthetic description';version=0}
    Call 'PUT' $categoryPath 409 @{name=$name;description='Stale';version=0} | Out-Null
    $category = Call 'PATCH' ($categoryPath+'/status') 200 @{status='DISABLED';version=1}
    Check ($category.version -eq 2 -and $category.status -eq 'DISABLED') 'Category status did not persist'
    $history = Call 'GET' ('/article-categories?keyword='+$name+'&status=DISABLED&size=1') 200
    Check ($history.items.Count -eq 1 -and $history.items[0].id -eq $category.id) 'Category filtering failed'
    Call 'POST' '/article-categories' 400 @{name=' ';description='invalid'} | Out-Null
    Call 'GET' '/article-categories?page=0' 400 | Out-Null
    Call 'GET' ('/article-categories/'+[guid]::NewGuid()) 404 | Out-Null
    Call 'PATCH' ($categoryPath+'/status') 400 @{status='WRONG';version=2} | Out-Null
    if ($Mode -eq 'category') {
        Call 'DELETE' ($categoryPath+'?version=2') 204
        Call 'GET' $categoryPath 404 | Out-Null
        Write-Output 'PASS: category create/read/update/delete, defaults, filters, status, 400/404/409'
        exit 0
    }
    $article = Call 'POST' '/articles' 201 @{title=$name;content='Synthetic article, not planting guidance';categoryId=$category.id}
    $articlePath = '/articles/' + $article.id
    Check ($article.status -eq 'DRAFT' -and $article.version -eq 0) 'Incorrect article defaults'
    $conflict = Call 'DELETE' ($categoryPath+'?version=2') 409
    Check ($conflict.code -eq 'DATA_CONFLICT') 'Referenced category deletion must fail'
    $article = Call 'PUT' $articlePath 200 @{title=$name;content='Updated synthetic article';categoryId=$category.id;version=0}
    $article = Call 'PATCH' ($articlePath+'/status') 200 @{status='PUBLISHED';version=1}
    $history = Call 'GET' ('/articles?keyword='+$name+'&status=PUBLISHED&size=1') 200
    Check ($history.items.Count -eq 1 -and $history.items[0].content -eq 'Updated synthetic article') 'Article filtering failed'
    Call 'PUT' $articlePath 409 @{title=$name;content='Stale';categoryId=$category.id;version=0} | Out-Null
    Call 'POST' '/articles' 404 @{title='missing category';content='test';categoryId=[guid]::NewGuid().ToString()} | Out-Null
    Call 'POST' '/articles' 400 @{title='bad category';content='test';categoryId='bad-id'} | Out-Null
    Call 'POST' '/articles' 400 @{title='empty';content=' ';categoryId=$category.id} | Out-Null
    Call 'PATCH' ($articlePath+'/status') 400 @{status='UNKNOWN';version=2} | Out-Null
    $session = Call 'POST' '/sessions' 201 @{title=$name;notes='Synthetic session, no AI required'}
    $sessionPath = '/sessions/' + $session.id
    Check ($session.status -eq 'OPEN' -and $session.version -eq 0) 'Incorrect session defaults'
    $session = Call 'PUT' $sessionPath 200 @{title=$name;notes='Updated session';version=0}
    $session = Call 'PATCH' ($sessionPath+'/status') 200 @{status='CLOSED';version=1}
    $history = Call 'GET' ('/sessions?keyword='+$name+'&status=CLOSED&size=1') 200
    Check ($history.items.Count -eq 1 -and $history.items[0].notes -eq 'Updated session') 'Session filtering failed'
    foreach ($path in @('/articles','/sessions')) {
        Call 'GET' ($path+'/invalid') 400 | Out-Null
        Call 'GET' ($path+'/'+[guid]::NewGuid()) 404 | Out-Null
        Call 'GET' ($path+'?size=101') 400 | Out-Null
        Call 'GET' ($path+'?status=INVALID') 400 | Out-Null
    }
    # Verify physical deletes only on newly-created synthetic records owned by this run.
    $tempSession = Call 'POST' '/sessions' 201 @{title='SYNTHETIC-DELETE';notes='delete check'}
    Call 'DELETE' ('/sessions/'+$tempSession.id+'?version=0') 204
    Call 'GET' ('/sessions/'+$tempSession.id) 404 | Out-Null
    $tempCategory = Call 'POST' '/article-categories' 201 @{name='SYNTHETIC-DELETE';description='delete check'}
    $tempArticle = Call 'POST' '/articles' 201 @{title='SYNTHETIC-DELETE';content='delete check';categoryId=$tempCategory.id}
    Call 'DELETE' ('/articles/'+$tempArticle.id+'?version=0') 204
    Call 'GET' ('/articles/'+$tempArticle.id) 404 | Out-Null
    Call 'DELETE' ('/article-categories/'+$tempCategory.id+'?version=0') 204
    Call 'GET' ('/article-categories/'+$tempCategory.id) 404 | Out-Null
    # Submit two updates based on the same version. Only one may become effective.
    $requests = @()
    $tasks = @()
    foreach ($answer in @('Concurrent-A','Concurrent-B')) {
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Put, ($BaseUrl+'/api/v1'+$sessionPath))
        $payload = @{title=$name;notes=$answer;version=2} | ConvertTo-Json
        $request.Content = New-Object System.Net.Http.StringContent($payload, [Text.Encoding]::UTF8, 'application/json')
        $requests += $request
        $tasks += $client.SendAsync($request)
    }
    $codes = @()
    try {
        foreach ($task in $tasks) {
            $response = $task.GetAwaiter().GetResult()
            try { $codes += [int]$response.StatusCode } finally { $response.Dispose() }
        }
    } finally { foreach ($request in $requests) { $request.Dispose() } }
    Check ((($codes | Sort-Object) -join ',') -eq '200,409') 'Concurrent updates must return one 200 and one 409'
    $session = Call 'GET' $sessionPath 200
    Check ($session.version -eq 3) 'Concurrent writes incremented version more than once'
    $saved = @(@{path=$categoryPath;record=(Call 'GET' $categoryPath 200)},
               @{path=$articlePath;record=(Call 'GET' $articlePath 200)},
               @{path=$sessionPath;record=(Call 'GET' $sessionPath 200)})
    New-Item -ItemType Directory -Force -Path (Split-Path $statePath -Parent) | Out-Null
    $saved | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Output 'PASS: three CRUD modules, defaults, filters, status, foreign key, 400/404/409, concurrent update; snapshots saved'
} finally { $client.Dispose(); $handler.Dispose() }
