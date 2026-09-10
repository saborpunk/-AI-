# 测试与验收

[返回 README](../README.md) · 环境准备见 [development](development.md)

## 当前结果与证据边界

最近业务验收：2026-09-09，代码提交[98cd597](https://github.com/saborpunk/-AI-/commit/98cd597)。环境为Windows、JDK21.0.12.1、Python3.12.10、现有MySQL8.4.9。Java 13项通过（0失败/错误/跳过），Python 9项通过。

2026-09-10仅重整文档，不重新运行业务测试、不访问数据库。下表保存最近有效业务结果，不代表本次重新测试，也不证明全新机器、生产部署、高并发容量或真实AI准确率。

## 自动测试

| 文件 | 数量 | 验证内容与替身边界 |
| --- | --- | --- |
| [HealthEndpointTest](../backend-java/src/test/java/com/seedassistant/HealthEndpointTest.java) | 1 | Java随机端口健康200、内部配置端点404；测试关闭DB健康探测，真实运行不关闭 |
| [DraftApiTest](../backend-java/src/test/java/com/seedassistant/DraftApiTest.java) | 5 | Java真实HTTP入口，JDK HttpServer模拟上游：有无批次、非法输入不调用上游、坏结果502、延迟504 |
| [ConsultationServiceTest](../backend-java/src/test/java/com/seedassistant/ConsultationServiceTest.java) | 5 | Mapper/客户端替身：不存在、旧版本、Python失败不写入、已确认禁止更新、条件更新失败回滚 |
| [DatabaseUnavailableApiTest](../backend-java/src/test/java/com/seedassistant/DatabaseUnavailableApiTest.java) | 2 | 测试DataSource抛连接异常，创建/历史503、非法问题400，不停止真实MySQL |
| [Python tests](../ai-service/tests) | 9 | 进程内健康、模板、有无批次、非法字段；不把“全部发芽”等输入当事实 |

Java完整构建测试，从根目录运行：

```powershell
. .\scripts\use-local-tools.ps1 -JdkHome 'D:\dev\sdk'
.\backend-java\mvnw.cmd -B -ntp -s config/maven-mirror.xml '-Dmaven.repo.local=.m2/repository' -f backend-java/pom.xml verify
```

报告在backend-java/target/surefire-reports。Python测试：

```powershell
Push-Location ai-service
.\.venv\Scripts\python.exe -m pip check
.\.venv\Scripts\python.exe -m pytest -q
Pop-Location
```

依次确认命令成功。单元测试验证规则，替身HTTP验证网络契约；都不能代替真实Java、Python和MySQL一起运行。

## 集成、冒烟与持久化结果

| 检查 | 最近实际结果 |
| --- | --- |
| 双服务健康与OpenAPI | smoke.ps1通过 |
| Java↔Python真实业务链 | smoke-draft.ps1通过；只请求Java，Python日志可见内部POST |
| 正常保存与查询 | 创建201，PENDING/version0；详情返回同一记录 |
| 草稿、编辑与确认 | draft_result落库；SAVE不覆盖原草稿；CONFIRM后只读 |
| 非法数据 | 空白/超长问题、非法批次/路径/分页/动作/版本返回400 |
| 不存在与冲突 | 查询和生成不存在记录404，旧版本/已确认操作409 |
| 历史分页 | 最近记录可查询，返回数量不超过size |
| 并发编辑 | 两个线程携带同一版本发送真实PATCH，恰好一个200、一个409，版本只加1 |
| Python停止 | 生成503，原记录保持不变；允许人工确认且原草稿为空 |
| MySQL不可用 | 此前真实端口拒绝连接时，创建/历史503；自动DataSource故障测试也通过 |
| 初始化重复执行 | 实际MySQL执行成功，再执行不清除已有记录 |
| Java重启读回 | 原问题、原草稿、人工回复、状态、版本、时间完整一致 |
| 最终回归 | 重启双服务后健康及原预览冒烟通过 |

并发测试是两个并发修改的正确性检查，不是压力测试，没有QPS/P95或百万数据结论。坏JSON另由原Java接口测试覆盖，不将脚本没检查的内容写成已检查。

### 正常路径复现

按development启动三个现有组件，在根目录依次运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-draft.ps1
.\ai-service\.venv\Scripts\python.exe scripts/check-persistence.py flow
```

flow创建明确标记的合成记录并保留，不删除其他数据；最新快照在忽略的.tools/persistence-check.json。脚本用httpx向真实Java发送请求，包含正常、错误、并发检查。

仅停止并重新启动本项目Java后：

```powershell
.\ai-service\.venv\Scripts\python.exe scripts/check-persistence.py readback
```

readback需此前flow产生的快照，不会自己重启服务。它比较整条响应，不能用它代替数据库备份恢复演练。

### 异常路径复现

保持Java与MySQL运行，只在自己的Python终端Ctrl+C停止Python，再运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-draft.ps1 -ExpectUnavailable
.\ai-service\.venv\Scripts\python.exe scripts/check-persistence.py python-unavailable
```

后者创建合成咨询，检查生成失败后记录未改变并可人工确认。完成后按development重启Python，重跑正常冒烟。

数据库不可用优先由自动测试模拟。只有现有MySQL本来就不可连接时，才执行：

```powershell
.\ai-service\.venv\Scripts\python.exe scripts/check-persistence.py database-unavailable
```

不得为了此检查停止MySQL、移动数据、改服务或建替代实例。脚本本身不停止任何服务。测试替身不是正常落库证据。

## 关键历史问题与现存提示

| 问题 | 处理与当前影响 |
| --- | --- |
| JDK尝试h2c，Uvicorn协议告警 | PythonDraftClient固定HTTP/1.1，复测通过，没有新增WebSocket库 |
| PowerShell5错误体为空 | smoke-draft兼容ErrorDetails和ResponseStream，故障响应复测通过 |
| Maven Central403 | 项目内可选HTTPS镜像恢复构建，未修改系统Maven |
| MySQL断连、1045 | 用户启动现有服务并核对本地认证配置后check成功，真实持久化复测通过；不记录凭据 |
| Python两条依赖弃用提示 | 既有Starlette/httpx测试后端与AnyIO提示，9项仍通过；升级依赖时复核 |
| Mockito动态agent、部分Jackson API弃用提示 | 测试通过但未隐藏提示；未来升级JDK/依赖时复核 |

## 文档整理检查（2026-09-10）

本次检查Markdown相对文件链接、代码引用位置、旧文档引用残留、敏感字段占位规则及Git差异范围。仅改变文档，不改业务代码，不将文档检查计入Java/Python测试数量。具体提交见[changelog](changelog.md)和Git历史。
