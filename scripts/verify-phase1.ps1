[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Assert-Equal {
    param(
        [string]$Name,
        $Actual,
        $Expected
    )

    if ($Actual -ne $Expected) {
        throw "$Name 验证失败：期望 $Expected，实际 $Actual"
    }

    Write-Host "[PASS] $Name = $Expected" -ForegroundColor Green
}

function Invoke-Chat {
    param([string]$Message)

    $json = @{
        message = $Message
    } | ConvertTo-Json -Compress

    Invoke-RestMethod `
        -Uri "$BaseUrl/api/chat" `
        -Method Post `
        -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes($json)) `
        -TimeoutSec 90
}

function Assert-ChatStatus {
    param(
        [string]$Message,
        [int]$ExpectedStatus
    )

    $json = @{
        message = $Message
    } | ConvertTo-Json -Compress

    try {
        $response = Invoke-WebRequest `
            -UseBasicParsing `
            -Uri "$BaseUrl/api/chat" `
            -Method Post `
            -ContentType "application/json; charset=utf-8" `
            -Body ([Text.Encoding]::UTF8.GetBytes($json)) `
            -TimeoutSec 30

        $actualStatus = [int]$response.StatusCode
    }
    catch {
        if ($null -eq $_.Exception.Response) {
            throw
        }

        $actualStatus = [int]$_.Exception.Response.StatusCode
    }

    Assert-Equal `
        -Name "聊天错误请求 HTTP 状态" `
        -Actual $actualStatus `
        -Expected $ExpectedStatus
}

try {
    Write-Host "`n==> 检查 Actuator" -ForegroundColor Cyan

    $health = Invoke-RestMethod `
        -Uri "$BaseUrl/actuator/health" `
        -TimeoutSec 10

    Assert-Equal "Application health" $health.status "UP"

    Write-Host "`n==> 检查 Swagger UI" -ForegroundColor Cyan

    $swagger = Invoke-WebRequest `
        -UseBasicParsing `
        -Uri "$BaseUrl/swagger-ui.html" `
        -TimeoutSec 10

    Assert-Equal "Swagger HTTP status" ([int]$swagger.StatusCode) 200

    Write-Host "`n==> 检查 MySQL、Redis 和模型配置" -ForegroundColor Cyan

    $system = Invoke-RestMethod `
        -Uri "$BaseUrl/api/system/status" `
        -TimeoutSec 20

    $system | ConvertTo-Json -Depth 5

    Assert-Equal "Application" $system.application "UP"
    Assert-Equal "MySQL" $system.mysql.status "UP"
    Assert-Equal "Redis" $system.redis.status "UP"
    Assert-Equal "LLM configured" $system.llmConfigured $true

    Write-Host "`n==> 连续调用真实模型三次" -ForegroundColor Cyan

    $questions = @(
        "请用两句话解释什么是 RAG。",
        "为什么科研文献检索不能直接让大模型生成论文列表？",
        "为什么 RAG 的引用 DOI 应来自检索元数据？"
    )

    for ($i = 0; $i -lt $questions.Count; $i++) {
        $response = Invoke-Chat -Message $questions[$i]

        if ([string]::IsNullOrWhiteSpace($response.answer)) {
            throw "第 $($i + 1) 次模型调用返回了空答案"
        }

        Write-Host (
            "[PASS] 第 {0} 次模型调用成功，答案长度：{1}" -f
            ($i + 1),
            $response.answer.Length
        ) -ForegroundColor Green
    }

    Write-Host "`n==> 检查参数校验" -ForegroundColor Cyan

    Assert-ChatStatus -Message "" -ExpectedStatus 400
    Assert-ChatStatus -Message ("a" * 4001) -ExpectedStatus 400

    Write-Host "`n第一阶段接口验收全部通过。" -ForegroundColor Green
    exit 0
}
catch {
    Write-Host "`n第一阶段接口验收失败。" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
