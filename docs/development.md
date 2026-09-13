# 启动、测试与排错

[项目入口](../README.md)

## 环境与首次准备

JDK21、MySQL8.4（现有localhost:3306）、Git；前端需Node，本机为24.20.0。前端测试jsdom30.0.1要求Node ^22.22.2 / ^24.15.0 / >=26。Maven Wrapper已提供。普通业务不需要Python或模型密钥。

以下命令在项目根目录执行，已有配置不要覆盖：

```powershell
Set-Location -LiteralPath 'C:\Users\PC\Desktop\智能化升级'
Set-ExecutionPolicy -Scope Process Bypass
. .\scripts\use-local-tools.ps1 -JdkHome 'D:\dev\sdk'
if (-not (Test-Path config/db.local.properties)) {
    Copy-Item config/db.example.properties config/db.local.properties
}
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/initialize-auth.ps1
.\backend-java\mvnw.cmd -B -ntp -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
```

在编辑器填写db.local.properties的spring.datasource.username/password，不打印或提交。initialize-auth生成32字节随机JWT密钥到被Git忽略的config/auth.local.properties；重复执行保留旧密钥。不得把这两个文件复制进前端。环境脚本只修改当前终端的Java工具路径和DEBUG=false，不改MySQL服务。

构建成功后，首次数据库按顺序初始化（任一步失败先处理，不继续）：

```powershell
$mysqlDriver = Get-ChildItem .m2\repository\com\mysql\mysql-connector-j -Recurse -Filter '*.jar' | Select-Object -First 1
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java check
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java initialize
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java initialize-v1
java -cp $mysqlDriver.FullName scripts/DatabaseSetup.java initialize-v2
```

V2升级到V3没有新DDL，仅需生成JWT密钥、下载新增Security/JOSE依赖并构建。历史记录不回填用户。所有SQL只操作项目库，不移动MySQL文件、不改端口或服务。

## 启动与商家设置

每个新终端都有自己的当前目录，不会自动继承首次配置终端的位置。以下路径对应本机；换电脑或移动项目后，替换项目路径和JDK路径。

终端一，完整复制执行：

```powershell
Set-Location -LiteralPath 'C:\Users\PC\Desktop\智能化升级'
$env:DEBUG = 'false'
& 'D:\dev\sdk\bin\java.exe' -jar .\backend-java\target\seed-service-0.1.0-SNAPSHOT.jar
```

终端二，完整复制执行：

```powershell
Set-Location -LiteralPath 'C:\Users\PC\Desktop\智能化升级'
node .\frontend\server.mjs
```

两个终端都保持运行，不关闭；停止对应服务用Ctrl+C。前端日常启动无需npm install。不要只改为绝对jar路径而仍留在用户目录：后端也需要从项目目录读取本地数据库与JWT配置。

浏览器打开 http://127.0.0.1:5173 ，注册客户账号后登录。后端健康检查为 http://127.0.0.1:8080/actuator/health 。两者仅监听回环地址。

要演示商家功能，先注册自己的账号，再在项目根目录执行（把your_username替换成该用户名）：

```powershell
$mysqlDriver = Get-ChildItem .m2\repository\com\mysql\mysql-connector-j -Recurse -Filter '*.jar' | Select-Object -First 1
java -cp $mysqlDriver.FullName scripts/UserAdmin.java your_username role MERCHANT
```

重新登录以更新页面角色展示；后端下一请求即读取新角色。脚本也支持`username role CUSTOMER`及`username status ENABLED|DISABLED`，只用于本地操作者，不提供网页提权入口。没有默认账号或通用演示密码。

JWT有效期15分钟，仅保存在页面内存，刷新后需重新登录。前端退出不提供服务端令牌撤销。SERVER_PORT可调整Java端口，但当前前端API地址固定8080；一般保持默认即可。

## 自动测试

构建前Ctrl+C停止本项目Java，避免Windows锁住jar。依赖缓存齐全时使用-o；首次下载去掉-o。

```powershell
.\backend-java\mvnw.cmd -B -ntp -o -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
```

默认35项通过，11项真实MySQL测试明确跳过。完成数据库初始化后，开启完整验证：

```powershell
$env:RUN_MYSQL_TESTS = 'true'
try {
    .\backend-java\mvnw.cmd -B -ntp -o -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
} finally { Remove-Item Env:RUN_MYSQL_TESTS -ErrorAction SilentlyContinue }
```

预期46项通过、0跳过。测试使用临时随机JWT密钥，不依赖真实密钥；数据库测试只操作随机合成记录，事务回滚或清理本次账号及会话。数据库故障通过替身模拟，不停止MySQL。

启动真实Java后，可在项目根目录验收并检查重启：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-auth.ps1 -Mode flow
# 只重启本项目Java，在15分钟内继续：
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-auth.ps1 -Mode readback
```

flow创建随机合成账号、授予该账号商家身份，调用原CRUD冒烟脚本；检查旧预览时应不启动Python。只在.tools/auth-check.json保存短期测试Token和账号ID，不保存原始密码；业务快照也保存在被Git忽略的.tools中。保留合成账号和主业务记录供读回，勿当真实客户数据。过期后重跑flow。

原check-business.ps1、smoke-draft.ps1、check-persistence.py现在从SEED_TEST_TOKEN环境变量读取商家JWT，不再匿名调用。不要在命令行参数或聊天中粘贴令牌。check-auth自动设置并恢复该环境变量。

前端DOM端到端测试需Java已启动；从项目根目录执行：

```powershell
npm --prefix frontend ci --ignore-scripts --no-audit --no-fund --cache .tools/npm-cache
$env:RUN_FRONTEND_INTEGRATION = 'true'
try {
    npm --prefix frontend test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend test failed' }
} finally { Remove-Item Env:RUN_FRONTEND_INTEGRATION -ErrorAction SilentlyContinue }
```

预期1项通过、0跳过。测试实际运行app.js并调用Java，创建合成客户、验证会话CRUD后删除本次会话；保留该合成客户，不输出密码或Token。未设置开关时该测试明确跳过。

## V3验证结果（2026-09-13）

| 验证 | 结果 |
| --- | --- |
| Java自动测试 | 46项通过，含11项真实MySQL，0失败/跳过 |
| JWT/角色 | 签名篡改、过期、缺失到期时间、错误issuer/audience、401/403、禁用/删除账号、实时角色变更通过 |
| 用户与隔离 | 注册固定客户、重复用户名、密码哈希、本人信息、会话归属、阻止跨客户读/改/删通过 |
| 原业务回归 | 认证后三模块CRUD、400/404/409、同版本并发一成功一冲突、旧预览503、旧咨询数据读回通过 |
| Java重启 | 用户资料、原Token与业务记录可读；签名密钥保持不变 |
| 前端DOM | 实际注册登录、创建/编辑/状态/删除、退出与再次登录通过；纯文本渲染用户输入、模拟401后清理状态通过 |
| 前端HTTP | 入口与资源200、CSP存在、非白名单私密路径404通过；CORS预检自动测试通过 |
| 故障 | 数据库503与认证故障区分；Python上游异常/超时原自动测试通过 |

当前没有可连接的浏览器，未完成真实浏览器点击、布局或移动端视觉验收；DOM测试不能替代这些检查。用户可按页面流程手动复核。没有性能压测、生产部署或AI新功能。

V2提交8737a00保留旧版本。V3修正原健康测试：未登录访问未开放的内部配置地址，由认证层先返回401，不再预期404；没有通过放开权限来迁就旧测试。

## 排错

| 现象 | 处理 |
| --- | --- |
| Unable to access jarfile / Cannot find module | 两个终端分别先执行上面的Set-Location；`Get-Location`应为项目目录。若目录正确但jar缺失，重新构建并确认BUILD SUCCESS |
| JWT key启动失败 | 运行initialize-auth.ps1；不要将随机密钥写入源码 |
| Maven403/首次缺依赖 | 使用项目mirror配置；首次不加-o |
| jar无法重命名 | 停止本项目Java再构建，不结束其他进程 |
| MySQL拒绝连接/1045 | 检查现有服务与本地凭据，不修改数据目录 |
| 401 | 重新登录；检查Bearer头、到期时间及账号状态 |
| 403 | 检查角色；客户无文章/分类管理和旧咨询权限 |
| 会话404 | 记录不存在或不属于当前客户；NULL归属仅商家可见 |
| 409 | 重新查询最新version，或检查用户名唯一/外键关联 |
| 503 | 数据库暂不可用；不要将它当成密码错误或盲目重复写入 |
| 前端连不上后端 | 保持Java8080、前端5173，从HTTP地址打开页面，不直接打开HTML文件 |
| 页面刷新后退出 | Token只在内存，是当前约定，不是数据库丢失 |
