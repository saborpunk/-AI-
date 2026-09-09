# 种子咨询与售后协作助手

面向柔毛淫羊藿种子商家的咨询处理项目，计划使用 Java 管理业务流程，Python 辅助生成回复草稿，由商家审核后在微信等渠道人工发送。

## 当前状态

已搭建 MVP v0.1 的第一阶段骨架：Java 和 Python 服务独立启动，提供健康接口、自动测试及冒烟脚本。尚未实现咨询、数据库、AI 生成、登录或前端；这不是完整 MVP。

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

预期输出 `PASS: Java health, Python mock health, Python OpenAPI`。健康检查只证明服务可响应，两个后端目前没有业务调用。

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
