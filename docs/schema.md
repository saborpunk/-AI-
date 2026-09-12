# 数据库结构

[项目入口](../README.md) · 数据库：seed_assistant；仅现有localhost:3306，MySQL8.4。

## 初始化脚本

- [001_create_consultation.sql](../scripts/sql/001_create_consultation.sql)：旧咨询表，不改已有记录。
- [002_create_traditional_business.sql](../scripts/sql/002_create_traditional_business.sql)：V1三张新表。
- [DatabaseSetup.java](../scripts/DatabaseSetup.java)：check检查连接；initialize执行001；initialize-v1执行002。命令见[启动说明](development.md)。

脚本只CREATE IF NOT EXISTS，不清空表，不自动升级已存在表。未操作MySQL安装、数据目录或服务配置。后续结构变化另加可审阅SQL，不修改已用表的数据文件。

## V1公共字段

三张表均使用InnoDB、utf8mb4。id为CHAR(36) UUID主键；version为非负BIGINT，默认0；created_at、updated_at为DATETIME(6)，UTC。更新显式写更新时间并增加版本；索引(created_at DESC,id DESC)支持历史排序。

| 表 | 业务字段 | 状态与约束 |
| --- | --- | --- |
| article_category | name VARCHAR(80)、description VARCHAR(500)，均非NULL | ENABLED（默认）/DISABLED，名称非空白 |
| knowledge_article | title VARCHAR(200)、content TEXT、category_id CHAR(36)，均非NULL | DRAFT（默认）/PUBLISHED，标题/内容非空白，category_id外键引用分类 |
| consultation_session | title VARCHAR(200)、notes VARCHAR(2000)，均非NULL | OPEN（默认）/CLOSED，标题非空白 |

description、notes可为空字符串，但不能缺省/null。API内容最大20000个Java字符。名称不强制唯一；分类重复命名当前允许。删除文章/会话为物理删除；分类被任何文章引用时删除被外键RESTRICT阻止，没有级联删除。

文章与分类是多对一关系；会话当前独立，不伪造user_id或消息表。用户与消息关系在后续版本引入。

## 旧咨询表（保留）

consultation：id、question、batch_code、status、draft_result JSON、final_answer、version、created_at、updated_at、confirmed_at。状态PENDING/DRAFT_READY/CONFIRMED；原草稿与人工稿分开。与新consultation_session不是同一张表，不自动迁移或互相覆盖。

V1验收记录以SYNTHETIC标识；只删除测试本次创建的临时记录，保留主测试记录供重启读回。真实资料仍未导入。
