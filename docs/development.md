# 开发环境与启动

[返回 README](../README.md) · 测试集中见 [testing](testing.md)

## 环境要求

| 工具 | 项目要求 / 最近实测 | 用途 |
| --- | --- | --- |
| Windows PowerShell | 当前启动命令按Windows编写 | 执行项目命令 |
| JDK | Java21；实测Temurin21.0.12.1，D:/dev/sdk | 编译、运行Java；按自己的安装路径调整 |
| Python | 3.12；实测3.12.10 | 运行AI服务和验收脚本 |
| MySQL | 现有8.4服务；实测8.4.9，localhost:3306 | 保存咨询 |
| Maven | Wrapper已提供；Maven3.9.16 / Wrapper3.3.4 | 无需另装Maven |
| Git | 可访问项目仓库 | 克隆、提交、推送 |

当前无需Node、Docker或模型密钥。Java依赖以[pom.xml](../backend-java/pom.xml)为准；Python以[requirements.txt](../ai-service/requirements.txt)固定版本安装，requirements.in解释直接依赖。首次下载需要网络。

## 首次准备

以下均从项目根目录执行，已有项目跳过克隆。

```powershell
git clone https://github.com/saborpunk/-AI-.git seed-service-assistant
cd seed-service-assistant
. .\scripts\use-local-tools.ps1 -JdkHome 'D:\dev\sdk'
java -version
.\backend-java\mvnw.cmd -version
py -3.12 -m venv ai-service/.venv
.\ai-service\.venv\Scripts\python.exe -m pip install -r ai-service/requirements.txt
.\backend-java\mvnw.cmd -B -ntp -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
```

只在上一条命令成功后继续。已有venv不用重复创建；若py找不到安装，改为已安装Python3.12的完整路径调用。脚本被执行策略阻止时，仅当前终端可用 `Set-ExecutionPolicy -Scope Process Bypass`。

### 数据库初始化

保留现有MySQL服务及安装、数据目录；本项目只通过localhost:3306访问，不新建实例、不移动物理数据、不修改服务配置。

```powershell
if (-not (Test-Path config/db.local.properties)) {
    Copy-Item config/db.example.properties config/db.local.properties
}
```

在编辑器中填写本地文件的 `spring.datasource.username` 和 `spring.datasource.password`。只使用自己获准访问项目库的账号，文档与提交中只保留占位符。此文件已被Git忽略，不要打印内容或把凭据放入命令行参数。Properties里的实际反斜线需要转义；不要凭猜测增删字符。

构建成功、驱动已下载后执行：

```powershell
$mysqlDriver = Get-ChildItem .m2\repository\com\mysql\mysql-connector-j -Recurse -Filter '*.jar' | Select-Object -First 1
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java check
# Only after check succeeds:
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java initialize
```

[DatabaseSetup.java](../scripts/DatabaseSetup.java)读取项目本地文件，check仅连接，initialize执行[项目SQL](../scripts/sql/001_create_consultation.sql)。初始化需创建项目库表的权限；权限不足由数据库所有者执行SQL。它创建seed_assistant.consultation，不删除记录、不调整账号；重复执行不清空数据，但也不会替已有表自动升级结构。它只读取本地Properties，不读取DB_USERNAME/DB_PASSWORD环境变量。

## 环境变量与本地配置

| 配置 | 当前作用 |
| --- | --- |
| JAVA_HOME、PATH | use-local-tools.ps1在当前终端选择JDK |
| MAVEN_USER_HOME | 脚本设为项目.m2，Wrapper工具缓存 |
| Maven -Dmaven.repo.local | 命令显式指定项目.m2/repository依赖缓存 |
| DEBUG | 脚本设false，避免系统同名变量意外打开调试 |
| AI_BASE_URL | 默认http://127.0.0.1:8000，指定Java联系Python的地址 |
| SERVER_PORT | 默认8080；只影响Java |
| DB_USERNAME / DB_PASSWORD | application.yml占位符备用来源；本地文件若直接定义spring.datasource同名属性，会覆盖该YAML占位符配置 |
| config/db.local.properties | 本机实际数据库属性；Spring从根目录或backend-java工作目录的相对路径导入 |
| config/maven-mirror.xml | Maven Central不通时的项目内公开镜像配置 |

优先只维护一份本地数据库配置，避免环境变量与文件同时配置导致误判。更高优先级的Spring命令行参数等仍可能覆盖配置。不要将秘密写入文档、Git或启动命令历史。

## 启动顺序

现有MySQL可连接并已建表 → Python → Java → 冒烟。两个应用先后启动并非强制依赖，但完整业务需要三者都可用。

终端A（项目根目录）：

```powershell
.\ai-service\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir ai-service --host 127.0.0.1 --port 8000
```

终端B（项目根目录）：

```powershell
. .\scripts\use-local-tools.ps1 -JdkHome 'D:\dev\sdk'
java -jar .\backend-java\target\seed-service-0.1.0-SNAPSHOT.jar
```

保持终端运行，Ctrl+C只停止该项目进程。Java health为 /actuator/health；Python health为 /health，接口清单为 /openapi.json，交互文档为 /docs（页面资源可能需联网）。服务只绑定本机，当前没有登录保护。

## 常见开发命令

| 目的 | 从项目根目录执行 |
| --- | --- |
| Java构建与测试 | ` .\backend-java\mvnw.cmd -B -ntp -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify ` |
| 依赖已缓存的离线构建 | 上述命令增加 `-o`，缺依赖时不能离线 |
| Python依赖检查 | ` .\ai-service\.venv\Scripts\python.exe -m pip check ` |
| Python测试 | 按[testing](testing.md)先切换到ai-service，再运行venv中的pytest |
| 健康冒烟 | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1` |
| 业务验收 | 见[testing](testing.md)，会创建合成记录 |
| 查看版本变化 | `git status --short`、`git diff`、`git log --oneline` |

Python测试使用ai-service工作目录，确保app包可导入。修改Java后先重新verify再启动jar；正在运行的jar不会自动变成新代码。所有本地缓存、虚拟环境和构建产物不提交。

## 常见故障排查

| 现象 | 检查和处理 |
| --- | --- |
| 找不到Java或版本错误 | 每个新Java终端重新dot-source环境脚本；检查JDK目录有bin/java.exe |
| Python找不到app或依赖 | 使用项目venv；Uvicorn从根目录加--app-dir ai-service；测试切到ai-service |
| Maven Central 403 | 使用项目config/maven-mirror.xml；不改系统Maven配置 |
| Maven TLS/代理错误 | 核对现有代理；确需代理时命令加 `'-Dhttps.proxyHost=127.0.0.1' '-Dhttps.proxyPort=7897'`，端口按实际调整，不关闭证书验证 |
| pip网络错误 | 核对代理；本机曾在当前终端设置NO_PROXY='*'并用清华HTTPS镜像恢复下载，不作为每台机器默认设置 |
| Maven误识别-D参数 | PowerShell中将整个带点的-D参数加单引号 |
| 大量DEBUG输出 | 用脚本将当前进程DEBUG设false，不改系统配置 |
| 数据库连接拒绝 / 503 | 确认用户现有服务已启动且localhost:3306可达，再运行check；不通过新实例或改端口绕过 |
| MySQL1045 | 核对现有账号及本地凭据格式，不打印密码、不猜测或重置账号 |
| DATABASE_ERROR / 表不存在 | 确认初始化SQL成功与项目库正确，不能只验证端口 |
| Python503 / 504 / 502 | 依次检查Python进程与端口、耗时、日志与JSON契约；数据库记录仍可人工处理 |
| 409 | 查询最新记录，检查版本及是否已确认，再由人工决定操作 |
| 端口冲突 | 不结束无关进程；可改Java SERVER_PORT，改Python端口时同步改AI_BASE_URL及测试脚本地址，MySQL仍固定3306 |
| GitHub TLS错误 | 先核对网络，必要时用命令级代理重试；commit是本地保存，push成功才上传 |

历史的HTTP/1.1、PowerShell错误体读取等代码修复见[testing](testing.md)，无需再安装WebSocket库。不要清理不明归属的MySQL遗留目录。
