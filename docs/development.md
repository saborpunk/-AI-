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
java -jar .\backend-java\target\seed-service-0.1.0-SNAPSHOT.jar
```

check/initialize工具只读本地Properties；需现有账号具有对应项目库表权限。不停止/重配MySQL、不创建新实例、不移动物理文件。默认Java端口8080，仅监听127.0.0.1；SERVER_PORT可改Java端口，MySQL仍3306。

## 测试命令

构建前Ctrl+C停止本项目Java，避免Windows锁住jar。依赖已有时可为Maven加-o离线构建。

```powershell
.\backend-java\mvnw.cmd -B -ntp -o -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
```

启动jar，新开终端运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-business.ps1 -Mode flow
# Restart only this project's Java, then:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-business.ps1 -Mode readback
```

flow创建SYNTHETIC记录，删除本次专用临时记录，保留主记录及.tools/business-check.json供readback；不删除其他记录。可用-BaseUrl指定Java地址。category模式只验分类。

## 本次验证记录（2026-09-12）

| 检查 | 结果 |
| --- | --- |
| Java自动测试 | 20项通过，0失败/错误/跳过：原13项+传统业务6项+新查询数据库故障1项 |
| 分类子阶段 | 编译及原13项通过；真实分类CRUD/状态/分页/400/404/409通过后才继续 |
| 三模块真实HTTP/MySQL | 新增、修改、详情、删除、条件分页、状态、默认值、400/404/409通过 |
| 关联与并发 | 被文章引用的分类删除409；两个同版本会话更新，一个200一个409 |
| 无Python运行 | 三模块flow及Java健康通过；未启动Python服务 |
| 应用重启 | 三模块记录、旧咨询问题/草稿/人工确认结果完整读回 |
| 旧生成故障兼容 | Python未运行时503/AI_UNAVAILABLE，符合旧约定 |
| 数据库异常 | 自动测试模拟DataSource故障验证新旧查询503，未停止真实MySQL |

Service测试使用Mapper替身；SQL正确性由真实API验证。没有压测、前端、JWT、真实模型或生产可用性验收。保留旧Python9项历史结果但本次未重跑，不算入本次20项。Git历史保留98cd597的旧持久化版本及7b24d39文档版本。

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

旧AI兼容验证需要Python时，沿用ai-service/requirements.txt与旧脚本；它不是当前启动步骤。当前不新增依赖，也不添加复杂运维组件。
