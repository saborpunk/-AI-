# 初学者学习与面试笔记

[返回 README](../README.md) · 当前设计见 [architecture](architecture.md) · 验证证据见 [testing](testing.md)

## 从业务开始，不急着背框架

本项目的使用者是商家：买家在微信问“发芽率多少”，商家把文字录入项目，获得待核实草稿，人工修改确认，再到微信手动回复。买家目前不直接使用这个系统，没有页面或自动微信发送。

| 业务词 | 简单解释 | 在当前代码中 |
| --- | --- | --- |
| 咨询 | 客户提出的一个问题 | consultation一条记录 |
| 批次 | 一批种子的标记 | batchCode只是可选编号，不等于已有检测结论 |
| 草稿 | 助手给商家的待检查文字 | draft_result，当前是Mock模板 |
| 人工回复 | 商家编辑后决定使用的文字 | final_answer，独立保存 |
| 确认 | 内部决定这份回复处理完成 | CONFIRMED；不表示客户已收到或问题已解决 |
| 持久化 | 程序重启后记录还在 | MySQL保存，不靠Java内存 |
| Mock | 提前写好的模拟处理 | 不接模型、不验证农业事实 |

先能说出“谁使用、输入什么、处理后得到什么”，再学下列语法。不要将宣传话术、计划中的数据量和未来能力写成项目事实。

## 阅读代码前先补的Java语法

每次只选一行对应代码，回答它的类型、值从哪来、下一步交给谁。无需一次读完全部模块。

| 语法 | 用白话解释 | 实际位置 / 必做练习 |
| --- | --- | --- |
| package / import | package表示类归属，import让你用短类名 | 打开ConsultationService，指出业务包与Spring导入；import不是创建对象 |
| class / interface | class描述对象；interface约定能调用哪些方法 | Service是类，Mapper是接口；解释为什么接口实例来自MyBatis代理 |
| public / private | 控制成员允许被哪里访问 | create对外调用，requireEditable仅类内部使用 |
| 构造器 / this / new | 构造器建立对象，this代表当前对象，new创建实例 | Service构造器的this.mapper=mapper；new TransactionTemplate(manager) |
| final | 字段赋值后不能重新指向别的对象 | private final mapper不等于数据库只读，也不等于引用对象深度不可变 |
| 方法 / 参数 / return | 方法接收参数、执行逻辑并把结果交回 | 手画create(Request)→View，不把返回给Service与返回HTTP混为一谈 |
| static与实例方法 | static属于类，不必先new该类；实例方法作用于对象 | UUID.randomUUID()与request.question()，说明两者调用差异 |
| 泛型 <T> | 给容器或包装指定内部数据类型 | ResponseEntity<Consultation.View>、List<View>；不是把尖括号里的对象自动创建出来 |
| record | 简洁的数据载体，自动提供访问器等 | Request.question()，区分Row/View；组件为List时不是深度不可变 |
| enum | 一组固定候选值 | ReviewAction.SAVE/CONFIRM；未知JSON动作被拒绝 |
| long / Long / null | long是基本类型；Long能表示null并可拆箱 | version请求用Long配@NotNull，Row用long；空Long拆箱会抛异常 |
| var | 编译器推断局部变量类型，仍然是静态类型 | var result=...；说出result实际是DraftModels.Result，不是任意类型 |
| if / != / || / 短路 | 条件决定执行路径；||左侧为真就不算右侧 | result==null || !validator.validate(result).isEmpty()避免空引用访问 |
| equals / == | 字符串内容用equals；==比较引用或基本值 | "PENDING".equals(row.status())；enum可用==；别用==比较字符串内容 |
| ?: 三元表达式 | 按条件选择两个值之一 | action==CONFIRM ? "CONFIRMED" : "DRAFT_READY" |
| throw / 异常传播 | 终止正常路径，把错误交给上层处理 | throw conflict()一路到ApiExceptionHandler；抛异常不是返回正常View |
| Lambda -> | 把一段行为传给方法执行，不会自动创建线程 | transaction.execute(ignored -> {...})是事务回调；return返回该回调的结果 |
| Stream / 方法引用 :: | 描述集合处理步骤，this::view代表对每个元素调用view | history的stream().limit(size).map(this::view).toList()；不是SQL LIMIT也不自动并行 |
| 注解 @ | 可由编译器/框架读取的元数据 | @RestController、@Service、@Mapper、@Valid各由不同机制处理 |
| try-with-resources | try括号内资源离开作用域自动close | DatabaseSetup中的connection/reader；事务提交不等同任意close操作 |

### 三小段实际代码，按语法读

`var result = service.create(request);`：调用service对象的方法，把request交进去，把返回值保存到局部变量result；var没有改变类型检查。

`mapper.review(...) != 1`：比较SQL影响行数，只有1行代表这次条件更新成功；0行可能是版本或状态改变，不应返回“修改成功”。

`transaction.execute(ignored -> { ... });`：把数据库操作交给事务模板；ignored是未使用的回调参数，箭头不表示异步。先理解这三句，再读网络客户端的链式调用。

## 按请求顺序的代码路标

建议分三次读：先创建，再草稿，最后人工确认。下面是路标，不需要背源码。

| 流程 | 读代码的顺序 | 停下来问自己 |
| --- | --- | --- |
| 创建 | Controller.create → DraftModels.Request/@Valid → Service.create → Mapper.insert/find → SQL默认值 → Service.view → 201 | 为什么没给status赋值也有PENDING？为什么插入后要读回？ |
| 草稿 | Controller.generate → Version → Service.generate → PythonDraftClient.generate → Python main/schemas/mock_generator → Mapper.saveDraft | 网络调用在哪个事务外？Python失败后有没有执行UPDATE？ |
| 确认 | Controller.review → Review → Service.requireEditable/review → Mapper.review → 影响行数 → View或409 | 为什么Java检查后SQL还检查？finalAnswer是否覆盖原草稿？ |
| 历史 | Controller.history → Service.history → Mapper.history → Page | 为什么多查一条？新增记录时OFFSET跨页会不会移动？ |

接口输入、字段、状态和完整数据流图只维护在[architecture](architecture.md)，避免多份文档越改越不一致。

## 面试问答卡片

每张卡片先读“是什么”，再去点代码，最后合上文档用自己的话回答。以下回答提示是理解方向，不是需要逐字背诵的台词。

### 项目整体介绍

- **是什么**：把重复咨询整理成可保存、可辅助起草、可人工确认的记录流程。
- **为什么项目需要**：商家在微信反复答疑且历史零散，先验证保存与确认闭环。
- **项目中在哪里使用**：[README](../README.md)；ConsultationController与Python模板。
- **面试可能怎么问**：请用一分钟介绍项目，你解决了什么？
- **回答要点**：面向单一种子商家，Java管理咨询及确认，Python提供发芽率Mock草稿，MySQL保存原问题、原稿和人工稿。完成故障接管、并发冲突及重启读回验证；还没有真实AI效果、真实客户省时或高并发指标。

### 一次请求完整链路

- **是什么**：一次HTTP请求经过格式转换、业务判断、数据访问和返回。
- **为什么项目需要**：能沿链路定位400、409、503发生在哪里。
- **项目中在哪里使用**：[ConsultationController.create](../backend-java/src/main/java/com/seedassistant/consultation/ConsultationController.java) → [Service.create](../backend-java/src/main/java/com/seedassistant/consultation/ConsultationService.java) → Mapper.insert/find。
- **面试可能怎么问**：点击创建后到底发生了什么？
- **回答要点**：先Filter生成requestId；Spring选择Controller、把JSON变成Request并@Valid；Service生成咨询UUID，事务内insert/find；Row转View，提交后返回201、Location和JSON。

### Spring Boot分层与依赖注入

- **是什么**：Boot组织应用配置；Controller接HTTP，Service组织业务，Mapper访问数据。
- **为什么项目需要**：把接待、规则和记账放在明确位置，便于测试和阅读。
- **项目中在哪里使用**：[启动类](../backend-java/src/main/java/com/seedassistant/SeedAssistantApplication.java)、Controller和Service构造方法。
- **面试可能怎么问**：为什么不把SQL都写Controller？谁new了Service？
- **回答要点**：可以写在一起但会混杂职责；Spring创建和注入组件，MyBatis提供Mapper代理。类有一个构造器时这里无需另加@Autowired。注解不是Java关键字，是框架读取的元数据。

### REST API

- **是什么**：通过HTTP方法、资源路径、状态码表达操作的接口风格。
- **为什么项目需要**：调用方能明确区分创建、查询和部分更新。
- **项目中在哪里使用**：ConsultationController的@PostMapping、@GetMapping、@PatchMapping。
- **面试可能怎么问**：POST与PATCH怎么选？接口符合什么约定？
- **回答要点**：创建POST返回201及Location；查询GET；人工更新PATCH。/draft是实际采用的动作子路径。当前不提供创建幂等键，重复POST会产生新咨询；不要声称所有操作都幂等。

### Java record与DTO

- **是什么**：DTO是传递数据的职责，record是简化数据载体的Java语法。
- **为什么项目需要**：固定请求字段，分开数据库Row与接口View。
- **项目中在哪里使用**：[DraftModels](../backend-java/src/main/java/com/seedassistant/draft/DraftModels.java)、[Consultation](../backend-java/src/main/java/com/seedassistant/consultation/Consultation.java)。
- **面试可能怎么问**：record与普通类有什么不同？它完全不可变吗？
- **回答要点**：自动生成构造器、访问器、equals/hashCode/toString；访问器是question()。组件引用不可重新赋值，但List等引用对象可能可变，因此不是深度不可变。

### @Valid与约束注解

- **是什么**：Bean Validation按字段约束检查对象，@Valid在接口参数上触发校验。
- **为什么项目需要**：空问题与非法批次应在业务处理前被拒绝。
- **项目中在哪里使用**：DraftModels.Request、Consultation.Version/Review、Controller参数；[ApiExceptionHandler.invalid](../backend-java/src/main/java/com/seedassistant/common/ApiExceptionHandler.java)。
- **面试可能怎么问**：有@NotBlank为什么还需要@Valid？@Pattern会拒绝null吗？
- **回答要点**：约束定义规则，执行入口触发检查；当前批次允许null，空串不匹配正则。@Valid不替你查询版本是否最新；损坏JSON在解析阶段被拒绝。直接调用Service不自动经过MVC的@Valid。

### RestClient

- **是什么**：Spring提供的同步HTTP客户端，底层本项目使用JDK HttpClient。
- **为什么项目需要**：让Java用统一JSON协议联系Python。
- **项目中在哪里使用**：[PythonDraftClient构造器和generate](../backend-java/src/main/java/com/seedassistant/draft/PythonDraftClient.java)。
- **面试可能怎么问**：为什么用RestClient而不是另加HTTP库？
- **回答要点**：复用Spring Web，调用容易顺序阅读；连接客户端复用，固定HTTP/1.1。代价是等待占用请求线程，不是异步处理。

### JDBC与连接池

- **是什么**：JDBC是Java数据库API，驱动完成MySQL协议通信，连接池复用连接。
- **为什么项目需要**：Mapper最终需要真实连接执行SQL。
- **项目中在哪里使用**：[pom.xml](../backend-java/pom.xml)、[application.yml](../backend-java/src/main/resources/application.yml)；[DatabaseSetup](../scripts/DatabaseSetup.java)显式使用DriverManager。
- **面试可能怎么问**：MyBatis已经操作数据库，为什么还要JDBC？
- **回答要点**：MyBatis封装映射，执行仍依赖JDBC；应用用Hikari连接池，初始化工具直接用DriverManager。最大5连接限制数据库资源使用，不等于HTTP只能接5个请求。

### Mapper与代理

- **是什么**：MyBatis把接口方法、参数和SQL关联，并创建代理实现。
- **为什么项目需要**：集中读写SQL，不手写重复的结果集映射。
- **项目中在哪里使用**：[ConsultationMapper](../backend-java/src/main/java/com/seedassistant/consultation/ConsultationMapper.java)的insert/find/history/saveDraft/review。
- **面试可能怎么问**：接口没有实现类，调用为什么能执行？
- **回答要点**：Spring集成MyBatis注册代理，代理读取注解执行SQL。insert/update返回影响行数；find返回Row或null。配置支持下划线字段到驼峰及record构造器映射。

### MySQL与数据模型

- **是什么**：关系数据库保存结构化台账，本表使用InnoDB。
- **为什么项目需要**：原问题和人工处理结果需跨Java重启保留。
- **项目中在哪里使用**：[建表SQL](../scripts/sql/001_create_consultation.sql)。
- **面试可能怎么问**：为什么一张表？JSON字段有什么代价？
- **回答要点**：当前一条咨询只保留一份成功原稿和当前人工稿，单表够用。JSON保留完整小快照，代价是多次生成历史和内部字段复杂检索尚未设计。主键、CHECK、非空约束保护结构，不证明内容正确。

### SQL参数绑定

- **是什么**：将SQL结构与用户数据分开传递。
- **为什么项目需要**：客户问题不能变成SQL语句的一部分。
- **项目中在哪里使用**：Mapper中的#{id}、#{question}等。
- **面试可能怎么问**：#{}与\${}区别？用了参数绑定是否万事安全？
- **回答要点**：#{}用于绑定数据，\${}做文本替换，拼入不可信输入会有风险。绑定不替代权限、业务校验，也不能用普通值参数随意替换列名；当前没有动态排序列。

### 事务

- **是什么**：把相关数据库操作放入共同提交或回滚的边界。
- **为什么项目需要**：更新后读回要与本次写入一起成功；出错不能被误报成功。
- **项目中在哪里使用**：Service中的TransactionTemplate.execute。
- **面试可能怎么问**：为什么不是一个@Transactional包住所有流程？
- **回答要点**：create在事务内insert/find；generate先HTTP后短事务saveDraft/find。外部HTTP不能随数据库回滚，长等待占连接；本项目用编程式事务明确范围。

### 乐观锁

- **是什么**：携带读取时的version，在更新时检查版本仍相同。
- **为什么项目需要**：保护商家刚修改的回复不被旧请求覆盖。
- **项目中在哪里使用**：Mapper.review/saveDraft的WHERE id/version/status，Service检查影响行数。
- **面试可能怎么问**：为什么先查版本后更新还不够？
- **回答要点**：检查与更新之间别人可能提交，真正防线是数据库条件UPDATE。乐观锁是策略，InnoDB更新仍会使用锁；不等于整个系统无锁。

### 并发冲突

- **是什么**：多个请求对同一旧版本提出修改，其中旧条件可能失效。
- **为什么项目需要**：让冲突可见，而非悄悄覆盖。
- **项目中在哪里使用**：[check-persistence.py](../scripts/check-persistence.py)的ThreadPoolExecutor并发PATCH；Service.conflict。
- **面试可能怎么问**：两个请求拿到version1如何处理？synchronized行不行？
- **回答要点**：A成功升为2，B影响0行得409。synchronized只约束相关JVM内的线程，不替代数据库跨进程条件。重新查询后由人工决定，不能盲目用新版号覆盖。当前只验证两个并发请求，不是压测成绩。

### HTTP超时

- **是什么**：连接和等待响应都有时间预算。
- **为什么项目需要**：避免Python无响应时无限占用Java请求线程。
- **项目中在哪里使用**：application.yml的2秒连接/3秒读取，PythonDraftClient工厂配置；DraftApiTest超时测试。
- **面试可能怎么问**：超时是不是Python一定没执行？
- **回答要点**：不是，可能已执行但响应没到。当前没有自动重试，不承诺只计算一次。不同超时预算也不等于严格统一的端到端总时限。

### 503、409及其他状态码

- **是什么**：状态码表达请求结果类别，code进一步说明原因。
- **为什么项目需要**：调用方需要区分改输入、处理冲突还是排查依赖服务。
- **项目中在哪里使用**：ApiExceptionHandler。
- **面试可能怎么问**：409和503分别该怎么处理？
- **回答要点**：409重新查状态/版本；503检查依赖可用性并核对已知记录。400输入无效、404记录不存在、502上游坏结果、504超时、500其他数据库错误。连接在提交时中断，不能断言数据肯定没写入。

### Java ↔ Python通信与契约

- **是什么**：双方通过JSON字段名、类型与约束交接，不共享Java对象。
- **为什么项目需要**：避免Python返回任意格式，或把其他请求的结果保存到这里。
- **项目中在哪里使用**：PythonDraftClient、[schemas.py](../ai-service/app/schemas.py)、[main.py](../ai-service/app/main.py)。
- **面试可能怎么问**：Python返回200为什么还要校验？requestId与咨询id区别？
- **回答要点**：200只表示HTTP成功；Java还检查字段、请求编号、mock/人工标识与缺失项。requestId跟踪一次调用，咨询id定位持久记录。Pydantic格式合格不等于农业事实可靠。

### 故障隔离与人工接管

- **是什么**：将助手能力与正式人工业务分开，失败时保留可用路径。
- **为什么项目需要**：助手不可用时仍需处理客户问题。
- **项目中在哪里使用**：Service.generate/review；DatabaseUnavailableApiTest与python-unavailable脚本模式。
- **面试可能怎么问**：AI挂了还能做什么？你实现了熔断吗？
- **回答要点**：已保存问题可查、可人工确认，前提是MySQL可用。MySQL不可用时旧无数据库预览仍可用；没有实现熔断、舱壁或完整高可用，隔离能力有明确边界。

### 当前为什么没有MQ

- **是什么**：MQ用于异步消息传输，不是每个AI应用必需组件。
- **为什么项目需要**：当前只需验证同步请求和持久化人工闭环。
- **项目中在哪里使用**：Service.generate直接调用Python，无生产者/消费者；[ADR-003](decisions.md)。
- **面试可能怎么问**：不用MQ怎么保证重启恢复生成任务？
- **回答要点**：当前不保证自动恢复进行中的生成，这是已知限制；保存的问题可以查询并按状态人工处理。若要可靠恢复，先设计持久任务/幂等/重试，再判断是否需要MQ。

### 并发量扩大如何演进

- **是什么**：根据实测瓶颈逐步调整容量和可靠性设计。
- **为什么项目需要**：现在同步等待、连接资源与OFFSET分页都有规模边界。
- **项目中在哪里使用**：Service.history、PythonDraftClient、Hikari配置；[架构演进](architecture.md)。
- **面试可能怎么问**：如何证明需要升级？会先上Redis还是MQ？
- **回答要点**：先测业务比例、模型耗时、请求P95、连接等待、慢SQL及冲突率。按瓶颈决定游标分页、索引、有界并发或持久任务；再评估缓存/MQ。多次调用费用、幂等与恢复单独设计，不直接宣称百万QPS。

## 复习路线与自检清单

| 次序 | 本次只学什么 | 合上文档后的验收 |
| --- | --- | --- |
| 1 | 业务词、HTTP/JSON、方法参数与返回值 | 用一分钟说清商家如何使用，明确目前没有网页或真实模型 |
| 2 | record、泛型、构造器、注解 | 解释Controller.create每个符号与Request字段 |
| 3 | Service、Lambda、Mapper、SQL默认值 | 按顺序画出一次创建，指出提交前后 |
| 4 | HTTP客户端、Python数据模型、异常 | 区分503/504/502，解释为什么200仍需校验 |
| 5 | Long/long、状态、条件UPDATE、事务 | 用A/B两个请求演示版本1如何变2、另一请求为何409 |
| 6 | 测试替身、真实HTTP、重启读回 | 区分13/9项自动测试与真实数据库验收，不把并发正确性说成性能指标 |
| 7 | Stream、分页、时间类型 | 解释hasMore、多查一条、UTC约定和Row/View区别 |

拓展学习放在理解现有代码之后：InnoDB隔离级别、索引执行计划、任务幂等与恢复。面试中不确定的问题可以明确说“当前未实现，这是下一步需要实验验证的设计”，不要把计划当成果。
