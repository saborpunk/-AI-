# 数据库结构

[项目入口](../README.md) · 数据库：seed_assistant；仅现有localhost:3306，MySQL8.4。

## 初始化脚本

- [001_create_consultation.sql](../scripts/sql/001_create_consultation.sql)：旧咨询表，不改已有记录。
- [002_create_traditional_business.sql](../scripts/sql/002_create_traditional_business.sql)：V1三张新表。
- [003_create_user_account.sql](../scripts/sql/003_create_user_account.sql)：空用户表，无预置密码。
- [004_add_session_owner.sql](../scripts/sql/004_add_session_owner.sql)：会话新增可空用户外键。
- [DatabaseSetup.java](../scripts/DatabaseSetup.java)：check检查连接；initialize执行001；initialize-v1执行002；initialize-v2执行003并按元数据检查决定是否执行004。命令见[启动说明](development.md)。

001至003使用CREATE IF NOT EXISTS。004用单个原子ALTER添加列、索引和外键；重复运行initialize-v2检查列和外键，不重复添加，不覆盖旧行。MySQL DDL隐式提交，用户表创建和会话ALTER不是同一事务；失败后修正权限或表结构再重跑。未操作MySQL安装、数据目录或服务配置。后续结构变化另加可审阅SQL，不修改已用表的数据文件。

## V1公共字段

三张表均使用InnoDB、utf8mb4。id为CHAR(36) UUID主键；version为非负BIGINT，默认0；created_at、updated_at为DATETIME(6)，UTC。更新显式写更新时间并增加版本；索引(created_at DESC,id DESC)支持历史排序。

| 表 | 业务字段 | 状态与约束 |
| --- | --- | --- |
| article_category | name VARCHAR(80)、description VARCHAR(500)，均非NULL | ENABLED（默认）/DISABLED，名称非空白 |
| knowledge_article | title VARCHAR(200)、content TEXT、category_id CHAR(36)，均非NULL | DRAFT（默认）/PUBLISHED，标题/内容非空白，category_id外键引用分类 |
| consultation_session | title VARCHAR(200)、notes VARCHAR(2000)，均非NULL | OPEN（默认）/CLOSED，标题非空白 |

description、notes可为空字符串，但不能缺省/null。API内容最大20000个Java字符。名称不强制唯一；分类重复命名当前允许。删除文章/会话为物理删除；分类被任何文章引用时删除被外键RESTRICT阻止，没有级联删除。

文章与分类多对一；会话user_id可空并引用user_account.id，多条会话可属于同一用户。索引(user_id,created_at DESC,id DESC)供后续按用户查询。NULL表示未核实归属，不创建占位用户。消息表留到后续。

## V2用户表 user_account

| 字段 | 类型/约束 |
| --- | --- |
| id | CHAR(36)，UUID主键 |
| username | VARCHAR(32)，ascii_bin唯一索引；3至32位小写字母、数字、下划线 |
| password_hash | VARCHAR(100)，当前仅BCrypt $2a$格式，实际60字符；不存原密码 |
| display_name | VARCHAR(80)，非空白 |
| role | CUSTOMER默认 / MERCHANT；注册固定客户；本地UserAdmin脚本可授予商家 |
| status | ENABLED默认 / DISABLED；UserAdmin脚本可修改；公开管理API留到V4 |
| version | BIGINT非负，默认0 |
| created_at / updated_at | DATETIME(6)，UTC默认值 |

Service校验密码至少12个Java字符、UTF-8最多72字节，不截断密码。数据库格式约束仅拦截错误格式，不能证明哈希来源；密码必须经过PasswordEncoder。用户名规范化由Service处理，唯一约束由数据库保证。

user_id外键RESTRICT，不级联删除会话。内部归属方法成功后增加version和updated_at，保留原title/notes/created_at；已归属或版本过期409。V2没有修改已有数据归属，V3不批量分配历史归属；新会话创建时写入认证用户ID。

## 旧咨询表（保留）

consultation：id、question、batch_code、status、draft_result JSON、final_answer、version、created_at、updated_at、confirmed_at。状态PENDING/DRAFT_READY/CONFIRMED；原草稿与人工稿分开。与新consultation_session不是同一张表，不自动迁移或互相覆盖。

V1验收记录以SYNTHETIC标识；只删除测试本次创建的临时记录，保留主测试记录供重启读回。真实资料仍未导入。

## V3变更

本版无新增表或DDL，继续使用001—004。新会话user_id在INSERT时赋值、初始version仍为0；旧NULL记录不回填，只有商家可读取。JWT不入库，不增加令牌表；数据库中的role/status每次请求读取。

[UserAdmin.java](../scripts/UserAdmin.java)只操作指定用户名的role或status，版本加1并更新时间，不修改密码和其他业务字段。操作值使用白名单，用户名绑定参数。后台Web用户管理留到V4。
