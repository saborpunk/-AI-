# 种子咨询业务后台

为种子商家的售前与售后咨询提供记录、文章管理和客户会话管理。先完成传统Java Web业务，再增加AI；现阶段不依赖Python或外部模型。

## 当前版本：V3 认证与前后端基础

- 客户注册、账号登录、JWT、查询当前用户；商家身份通过本地管理脚本授予。
- 文章分类、知识文章和会话CRUD；客户只能访问自己的会话，商家可管理全部记录。
- 独立前端提供注册登录、会话创建/编辑/状态/删除，商家可查看文章。完整工作台留到V4。
- 旧咨询数据保留，旧接口现须商家JWT；新增会话自动归属登录用户。

## 技术与架构

Java21 / Spring Boot4.0.8 / Spring Security / JWT / MyBatis-Plus3.5.17 / MySQL8.4。
前端使用原生HTML/CSS/JavaScript和Node静态服务器；jsdom仅用于测试。

```text
前端 :5173 → HTTP + Bearer JWT → Spring Security → Controller
                                                   ↓
                                                Service
                                                   ↓
                                                Mapper → MySQL :3306
```

## 最短启动

首次环境、数据库初始化及V2升级见[启动说明](docs/development.md)。本地数据库配置不提交。

```powershell
Set-Location -LiteralPath 'C:\Users\PC\Desktop\智能化升级'
Set-ExecutionPolicy -Scope Process Bypass
. .\scripts\use-local-tools.ps1 -JdkHome 'D:\dev\sdk'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/initialize-auth.ps1
.\backend-java\mvnw.cmd -B -ntp -o -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
java -jar .\backend-java\target\seed-service-0.1.0-SNAPSHOT.jar
```

新终端不会继承项目目录，终端二完整执行：

```powershell
Set-Location -LiteralPath 'C:\Users\PC\Desktop\智能化升级'
node .\frontend\server.mjs
```

打开 http://127.0.0.1:5173 。上面的路径对应本机，其他位置请替换；两个终端保持运行。首次下载Java依赖去掉-o；前端日常启动无需npm install。已完成首次配置时，两个终端的最短命令见[启动说明](docs/development.md#启动与商家设置)。

注册客户账号 → 登录 → 创建咨询 → 查看并修改记录。JWT有效期15分钟，页面刷新后重新登录。商家设置方式见启动说明。

## 验证与文档

2026-09-13：Java46项测试通过（含11项真实MySQL测试，0跳过），认证后的CRUD、并发冲突、旧数据兼容及Java重启读回通过。前端验证结果和环境限制见启动说明。

- [架构与阶段路线](docs/architecture.md)
- [数据库结构](docs/schema.md)
- [API及权限说明](docs/api.md)
- [启动、测试与排错](docs/development.md)

本版仅本机演示，不提供完整用户管理、刷新令牌或生产部署。下一版V4完善传统业务工作台；之后再做Spring AI、外部模型和SSE。
