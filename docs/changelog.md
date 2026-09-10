# 开发时间线

[返回 README](../README.md) · 当前最终测试详情仅见 [testing](testing.md)

## MVP阶段1：项目骨架（2026-09-09）

- **实现了什么**：Java/Python最小HTTP服务、健康接口、Maven Wrapper、Python虚拟环境、启动和测试入口。
- **解决了什么关键问题**：JDK/Python版本选择、代理与依赖下载、DEBUG环境变量、PowerShell脚本兼容；范围复核撤回越阶段代码，保留现有MySQL服务及物理目录边界，不再执行旧临时实例或目录清理安排。
- **测试结果**：当时Java1项、Python1项及双服务健康冒烟通过；这是历史数量，不是当前总数。
- **Git commit**：[`a3a24f4`](https://github.com/saborpunk/-AI-/commit/a3a24f4) 骨架；[`778a061`](https://github.com/saborpunk/-AI-/commit/778a061) 范围复验；[`fdb003c`](https://github.com/saborpunk/-AI-/commit/fdb003c) MySQL边界。

## MVP阶段2：Java ↔ Python（2026-09-09）

- **实现了什么**：发芽率Mock预览，Java参数校验、同步HTTP、响应契约检查、requestId和结构化错误。
- **解决了什么关键问题**：固定HTTP/1.1解决h2c告警；PowerShell兼容读取错误JSON；无模型密钥也能验收业务交接。
- **测试结果**：当时Java6项、Python9项通过；真实双服务与Python停止503验证通过。
- **Git commit**：[`7071757`](https://github.com/saborpunk/-AI-/commit/7071757)。

## MVP阶段3：MySQL持久化闭环（2026-09-09）

- **实现了什么**：保存咨询、原草稿、人工回复与确认状态，详情/历史分页，短事务、版本条件更新。
- **解决了什么关键问题**：现有MySQL连接及认证排错；项目镜像解决Maven403；并发修改防覆盖、Python失败人工接管；未变更MySQL安装/数据目录或服务配置。
- **测试结果**：Java13项、Python9项；真实MySQL保存/修改/确认、400/404/409、并发一成功一冲突、Python停止人工接管、重复初始化与Java重启读回、旧接口回归均通过。
- **Git commit**：[`98cd597`](https://github.com/saborpunk/-AI-/commit/98cd597)，已推送远端main；最初TLS失败后重试推送成功。

## 文档整理（2026-09-10，非新开发阶段）

- **实现了什么**：README唯一入口及六份核心docs，合并旧阶段文档；新增业务入门、实际Java语法与面试问答。
- **解决了什么关键问题**：移除过期“数据库尚未实现”等描述；当前实现与长期计划分开；保留用户协作要求。
- **测试结果**：仅文档链接、引用和变更范围检查，不重新运行服务或数据库测试；业务结果沿用98cd597的已验收记录。
- **Git commit**：本条随 `docs: 整理核心文档与 Java 入门面试路线` 提交，可用 `git log --oneline` 定位，不虚构尚未生成的提交号。
