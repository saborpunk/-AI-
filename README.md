# 种子咨询与售后协作助手

面向柔毛淫羊藿种子商家的咨询处理项目，计划使用 Java 管理业务流程，Python 辅助生成回复草稿，由商家审核后在微信等渠道人工发送。

## 当前状态

已实现 MVP v0.1 第二阶段的单条调用链：Java 接收发芽率咨询、校验参数，调用 Python Mock 生成待核实草稿，再校验并返回 JSON。尚未实现持久化、真实 AI、登录或前端；这不是完整 MVP。

- [项目整体计划书](项目整体计划书.md)：业务痛点、技术说明及长期建设路线。
- [项目长期协作说明](AGENTS.md)：版本提交规则和面向初学者的解释要求。
- [MVP 范围](docs/MVP-v0.1.md)：当前必做、暂缓项和分阶段验收。
- [技术注解](docs/技术决策.md)：区分前后端，用业务类比解释各项技术。
- [开发记录](docs/开发记录.md)、[测试报告](docs/测试报告.md)、[面试学习笔记](docs/面试学习笔记.md)。

目标流程：录入文字咨询 → 根据模拟资料生成草稿 → 商家编辑确认 → 查看历史。本阶段无需真实资料、模型密钥或 Docker。

## Windows 启动步骤

准备 JDK 21、Python 3.12（含 pip）和 Git。Maven Wrapper 已包含在仓库中，第一次使用会下载 Maven 3.9.16，无需单独安装 Maven。以下命令在项目根目录的 PowerShell 执行。

1. 克隆项目并配置当前终端的 JDK 路径。已在本地项目目录的用户跳过 clone 和 cd。

```powershell
git clone https://github.com/saborpunk/-AI-.git seed-service-assistant
cd seed-service-assistant
# 将路径改成你的 JDK 根目录，目录下面应该有 bin/java.exe。
. .\scripts\use-local-tools.ps1 -JdkHome 'D:\dev\sdk'
java -version
.\backend-java\mvnw.cmd -version
```

脚本只设置当前终端环境，不更改 Windows 全局配置。若脚本被执行策略阻止，可仅在当前终端执行 `Set-ExecutionPolicy -Scope Process Bypass` 后重试。

2. 编译、测试 Java，打包并启动。第一次构建需要网络。

```powershell
.\backend-java\mvnw.cmd -B -ntp '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
# 仅在上面的命令显示 BUILD SUCCESS 后启动。
java -jar .\backend-java\target\seed-service-0.1.0-SNAPSHOT.jar
```

保持终端运行。浏览器访问 http://127.0.0.1:8080/actuator/health，应看到 `{"status":"UP"}`。jar 是打包后的 Java 程序；Ctrl+C 停止它。

3. 新开一个 PowerShell，进入同一个项目根目录，创建 Python 虚拟环境并安装固定版本依赖。

```powershell
py -3.12 -m venv ai-service/.venv
.\ai-service\.venv\Scripts\python.exe -m pip install -r ai-service/requirements.txt
cd ai-service
.\.venv\Scripts\python.exe -m pip check
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

如果 `py -3.12` 找不到已安装的 Python，可将第一行替换为完整路径调用，例如：

```powershell
& "$env:LOCALAPPDATA\Programs\Python\Python312\python.exe" -m venv ai-service/.venv
```

浏览器访问 http://127.0.0.1:8000/health，应看到 `status=UP`、`service=seed-ai`、`mode=mock`。接口清单位于 http://127.0.0.1:8000/openapi.json；交互文档位于 http://127.0.0.1:8000/docs（其页面静态资源可能需要联网）。Ctrl+C 停止服务。

4. 两个服务都启动后，新开终端，在项目根目录运行：

```powershell
.\scripts\smoke.ps1
```

预期输出 `PASS: Java health, Python mock health, Python OpenAPI`。这只检查健康状态；继续执行下面的业务冒烟，才能验证跨服务调用。

## 第二阶段：发芽率草稿预览

代码更新后先按上面步骤重新构建 Java、重启两个服务。此功能没有数据库，不需要 MySQL。

```powershell
# 在项目根目录运行：真实请求只发往 Java，由 Java 调用 Python。
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-draft.ps1

$body = @{ question = '这个种子发芽率多高？'; batchCode = 'DEMO-001' } | ConvertTo-Json
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/germination-drafts' `
  -Method Post -ContentType 'application/json; charset=utf-8' `
  -Body ([Text.Encoding]::UTF8.GetBytes($body)) | ConvertTo-Json -Depth 5
```

响应示例（requestId 每次不同）：

```json
{
  "requestId": "服务端生成的UUID",
  "data": {
    "requestId": "与外层相同的UUID",
    "answerDraft": "【模拟草稿】已收到批次号 DEMO-001，还需要商家提供并核对该批次的检测或试种记录。当前无法确认发芽率，请勿将宣传话术当作批次检测结论。",
    "missingFields": ["batchEvidence"],
    "needsHumanReview": true,
    "mode": "mock"
  }
}
```

question 必填，不能全空白，最多 2000 个字符。batchCode 可不传或为 null；传入时为 1–40 位大写字母、数字或短横线。不传批次时，missingFields 同时返回 batchCode、batchEvidence。有批次号不等于有检测依据，此阶段不会返回发芽率数字，也不会将任意问题自动分类。

| 情况 | HTTP 状态 | code |
| --- | --- | --- |
| 正常 Mock 草稿 | 200 | 成功响应使用 data |
| 参数或 JSON 无效 | 400 | INVALID_REQUEST |
| Python 连接失败 | 503 | AI_UNAVAILABLE |
| Python 响应超时 | 504 | AI_TIMEOUT |
| Python 非成功状态或内容不符合约定 | 502 | AI_BAD_RESPONSE |

错误 JSON 包含 code、message、requestId，响应头 X-Request-Id 与编号一致。默认连接超时 2 秒、读取超时 3 秒，不配置自动重试。Python 地址由启动 Java 前设置的 AI_BASE_URL 覆盖，默认 http://127.0.0.1:8000；不能通过请求体指定任意上游地址。

详细文件职责、调用流程与取舍见 [第二阶段说明](docs/第二阶段说明.md)。

## 网络和常见问题

- Maven 首次失败若是 TLS/代理错误，先确认自己的代理实际可用。需要通过代理下载时，可在构建命令增加 `'-Dhttps.proxyHost=127.0.0.1' '-Dhttps.proxyPort=7897'`；7897 是本机实测端口，其他电脑需换成自己的端口。不要关闭证书验证。
- pip 如被系统代理干扰，可在该终端临时设置 `$env:NO_PROXY='*'`，使用 `python.exe -m pip install -r ai-service/requirements.txt --index-url https://pypi.tuna.tsinghua.edu.cn/simple` 从清华镜像下载。此处 python.exe 指上面的虚拟环境完整路径。关闭终端后临时设置结束。
- PowerShell 中 Maven 的 `-D` 参数整体加引号，避免带点号的版本号被拆分。
- 端口被占用时，不要随意结束其他项目进程。Java 可在启动前设置 `$env:SERVER_PORT='8081'`，Python 可改 `--port 8001`，检查脚本对应使用 `-JavaBaseUrl http://127.0.0.1:8081 -AiBaseUrl http://127.0.0.1:8001`。
- 服务只监听本机。数据库、鉴权和部署方案在后续阶段加入。

## 目录与依赖

```text
backend-java/   Java 启动类、健康配置、测试、Maven Wrapper
ai-service/     Python HTTP 服务、测试、requirements.txt 固定依赖
scripts/        当前终端环境设置和实际 HTTP 冒烟检查
docs/           MVP 范围、技术决策、开发与测试记录、面试笔记
```

Python 的 `requirements.in` 记录直接依赖及用途，`requirements.txt` 固定本次测试过的全部依赖版本。普通安装使用 txt；升级时才修改 in、重新解析版本并回归测试。`.tools`、`.m2`、`.venv` 和 `target` 是本地工具或产物，不上传 Git。

## 版本管理入门

- Git：记录文件变化的工具，可以理解为项目的版本相册。
- commit：给当前变化拍一张有说明的快照，保存在本地。
- 远程仓库：保存项目版本的线上位置，例如 GitHub 或 Gitee。
- push：将本地快照上传到远程仓库。

每完成一版并验证通过后创建新提交并推送。不要将模拟结果写成真实客户效果、真实 AI 准确率或已实现的高并发能力。
