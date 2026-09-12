# 启动与验证

[项目入口](../README.md)

## 环境

Windows PowerShell、JDK21（本机D:/dev/sdk）、现有MySQL8.4 localhost:3306、Git。Maven Wrapper随项目提供；新业务无需Python、Node或模型密钥。首次构建需网络。

## 首次准备

在项目根目录执行；已有本地配置不要覆盖。

```powershell
Set-ExecutionPolicy -Scope Process Bypass
. .\scripts\use-local-tools.ps1 -JdkHome 'D:\dev\sdk'
if (-not (Test-Path config/db.local.properties)) {
    Copy-Item config/db.example.properties config/db.local.properties
}
.\backend-java\mvnw.cmd -B -ntp -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
```

在编辑器填本地文件的spring.datasource.username/password，禁止上传或打印。脚本只设置当前终端JAVA_HOME、PATH、MAVEN_USER_HOME和DEBUG=false；密码文件被Git忽略。不同终端需重新执行环境脚本。

构建成功后，按顺序检查并初始化（只在前一条成功后继续）：

```powershell
$mysqlDriver = Get-ChildItem .m2\repository\com\mysql\mysql-connector-j -Recurse -Filter '*.jar' | Select-Object -First 1
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java check
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java initialize
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java initialize-v1
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java initialize-v2
java -jar .\backend-java\target\seed-service-0.1.0-SNAPSHOT.jar
```

check/initialize工具只读本地Properties；需现有账号具有对应项目库表权限。不停止/重配MySQL、不创建新实例、不移动物理文件。默认Java端口8080，仅监听127.0.0.1；SERVER_PORT可改Java端口，MySQL仍3306。

从V1升级：先停止本项目Java，执行initialize-v2，再构建启动。用户表与可空user_id是本版新增；不要只换jar而漏掉SQL。新依赖为MyBatis-Plus3.5.17 Boot4 Starter与Boot管理版本的spring-security-crypto，分别简化Mapper基础SQL和提供BCrypt；首次迁移构建不加-o。

## 测试命令

构建前Ctrl+C停止本项目Java，避免Windows锁住jar。依赖已有时可为Maven加-o离线构建。

```powershell
.\backend-java\mvnw.cmd -B -ntp -o -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
```

默认构建运行26项测试，另外7项真实MySQL测试明确跳过。执行initialize-v2后，用以下命令开启完整验证：

```powershell
$env:RUN_MYSQL_TESTS = 'true'
try {
    .\backend-java\mvnw.cmd -B -ntp -o -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
} finally { Remove-Item Env:RUN_MYSQL_TESTS -ErrorAction SilentlyContinue }
```

UserPersistenceTest只创建随机合成账号和会话：事务测试结束回滚，并发测试提交后清理本次随机账号。它使用项目现有数据库，不启停MySQL，不创建替代实例。预期33项通过、0跳过。

启动jar，新开终端运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-business.ps1 -Mode flow
# Restart only this project's Java, then:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-business.ps1 -Mode readback
```

flow创建SYNTHETIC记录，删除本次专用临时记录，保留主记录及.tools/business-check.json供readback；不删除其他记录。可用-BaseUrl指定Java地址。category模式只验分类。

## V2验证记录（2026-09-12）

| 检查 | 结果 |
| --- | --- |
| 自动测试 | 33项通过，0失败/错误/跳过：原20项、用户服务6项、真实MySQL7项 |
| 用户持久化 | 用户名规范化、默认角色/状态/版本/时间、中文映射、BCrypt匹配与随机盐、响应不含密码通过 |
| 用户异常与并发 | 非法输入400、缺失404、相同用户名并发一成功一409、SQL参数绑定、CHECK约束通过 |
| 会话归属 | NULL可读、首次分配成功、再次分配409、不存在用户404、外键限制与事务回滚通过 |
| 迁移与真实HTTP | initialize-v2执行两次成功；三模块CRUD、默认值、条件分页、状态、400/404/409、关联保护通过 |
| 并发更新 | 两个同版本会话请求一200一409，最终version仅加1 |
| 升级与重启 | 升级前后三模块JSON一致；V2创建记录在Java重启后完整读回；旧咨询JSON/草稿/人工结果可读 |
| 故障兼容 | 模拟DataSource故障返回503；旧Java↔Python自动测试通过；真实Python未启动时旧预览503/AI_UNAVAILABLE |

没有启动Python服务，也未停止MySQL。尚无用户HTTP接口、JWT、前端或AI扩展测试；用户数据服务直接通过Spring集成测试验证。V1提交c73409d保留原MyBatis三模块版本；旧双服务实现仍可从Git历史追溯。

关键修正：自定义写方法与BaseMapper同名会占用框架SQL标识，已改为insertRow/updateVersioned/deleteVersioned。MySQL CHECK返回3819/HY000，测试改为核对真实错误码，不错误假定Spring包装类型。

## 排错

| 现象 | 处理 |
| --- | --- |
| PowerShell禁止脚本 | 只对当前进程设置ExecutionPolicy Bypass，不改系统策略 |
| jar无法重命名 | 停止本项目旧Java进程再构建，不结束无关进程 |
| 初始化1064 | 查看项目SQL；工具按分号切语句，脚本注释不写分号；本次已修正 |
| MySQL拒绝连接/1045 | 用户启动现有服务或核对本地认证，不能改数据目录或猜密码 |
| Maven403 | 使用项目内config/maven-mirror.xml，不修改全局Maven |
| 400/404/409 | 按API检查字段、记录是否存在、version及关联文章 |
| 503 | 检查现有MySQL连接；提交中断后先查询结果，不能断言没写入 |

旧AI兼容验证需要Python时，沿用ai-service/requirements.txt与旧脚本；它不是当前启动步骤。当前不添加复杂运维组件。
