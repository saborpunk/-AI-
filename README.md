# 种子咨询业务后台

项目方向：先完成传统 Spring Boot 前后端分离业务，再增加 Spring AI、外部模型和 SSE。服务对象是客户与商家；当前 V1 仅提供本机开发 API，尚无账号、权限和前端。

## 当前版本：V1 传统 CRUD

已完成文章分类、知识文章、咨询会话三个模块：创建、详情、修改、删除、条件分页及状态修改。使用真实 MySQL；不需要运行 Python 或外部模型。

旧咨询草稿接口及数据保留作兼容，未继续扩展 AI。新业务层没有 AI/Python 依赖。现有版本不是已完成认证的生产服务。

## 技术与结构

Java21 / Spring Boot4.0.8 / Spring MVC / Validation / MyBatis4.0.0 / MySQL8.4。没有新增依赖；MyBatis-Plus 放到 V2 作为单独迁移验证。

```text
HTTP / JSON → Controller → Service → Mapper → MySQL
                         └─ 当前三个模块不调用 AI
```

新业务目录：controller、service、mapper、entity、dto、common。原 consultation、draft 包暂保留旧接口，不作为新业务底座。

## 启动

首次准备见[启动说明](docs/development.md)。项目根目录 PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
. .\scripts\use-local-tools.ps1 -JdkHome 'D:\dev\sdk'
.\backend-java\mvnw.cmd -B -ntp -o -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
java -jar .\backend-java\target\seed-service-0.1.0-SNAPSHOT.jar
```

首次下载依赖去掉 `-o`，只有 BUILD SUCCESS 后启动。现有 MySQL 须在 localhost:3306；数据库本地配置不提交。健康入口：http://127.0.0.1:8080/actuator/health。

## 验证与文档

2026-09-12：Java20项测试通过；三个CRUD、外键保护、400/404/409、并发修改、新旧记录重启读回通过。新业务验收未启动Python。详细命令和结果在启动说明。

- [架构与分阶段路线](docs/architecture.md)
- [数据库表结构](docs/schema.md)
- [API说明](docs/api.md)
- [启动、测试与排错](docs/development.md)

阶段完成后提交Git并等待下一阶段确认。暂不增加Redis、MQ、微服务或高并发组件。
