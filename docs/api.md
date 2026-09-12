# API说明

[项目入口](../README.md) · 基址 `http://127.0.0.1:8080/api/v1` · JSON请求 · 当前仅本机开发，无JWT认证。

## V2兼容边界

本版不新增用户HTTP接口。用户表、密码哈希和归属只由内部Service及测试验证；不提供匿名用户查询、创建商家或会话认领接口。原三个CRUD的URL、JSON字段、状态码和响应头不变，user_id不出现在会话响应中。公开注册、登录及用户信息接口留到V3。

## 三个CRUD模块

路径前缀：`/article-categories`、`/articles`、`/sessions`。

| 方法与相对路径 | 功能 | 成功状态 |
| --- | --- | --- |
| POST /前缀 | 创建 | 201，Location与记录JSON |
| GET /前缀/{id} | 详情 | 200 |
| GET /前缀?keyword=&status=&page=1&size=20 | 条件分页 | 200 |
| PUT /前缀/{id} | 修改业务字段，必须携带version | 200 |
| PATCH /前缀/{id}/status | 修改状态，必须携带version | 200 |
| DELETE /前缀/{id}?version=0 | 物理删除 | 204，无正文 |

上述“前缀”替换为article-categories、articles或sessions，不重复加斜杠。id为UUID。keyword按分类名称/文章标题/会话标题做字面子串搜索；status不传表示不过滤，传空串或未知值400。页码1–1000，size1–100；响应为items/page/size/hasMore，不提供总数。

### 创建请求

分类：

```json
{"name":"模拟分类","description":"仅用于演示"}
```

文章（categoryId使用创建分类后返回的UUID）：

```json
{"title":"模拟文章","content":"仅用于接口演示，不是种植指导","categoryId":"替换为实际分类UUID"}
```

会话：

```json
{"title":"模拟咨询会话","notes":"人工记录，不调用AI"}
```

PUT提交同样的完整业务字段，另外增加最新version；不通过PUT修改status。名称80字符，标题200字符，分类说明500字符，内容20000字符，备注2000字符。名称/标题/内容不能为空白；description/notes必须提供但允许空串。

### 状态修改

```json
{"status":"PUBLISHED","version":1}
```

分类状态ENABLED/DISABLED；文章DRAFT/PUBLISHED；会话OPEN/CLOSED。每次有效更新，包括提交相同内容，也增加version。并发旧版本返回409，重新查询后再决定修改，不自动覆盖。

返回字段为业务字段加id/status/version/createdAt/updatedAt。时间按UTC解释。请求都带X-Request-Id响应头；201另带Location。

## 错误响应

```json
{"code":"VERSION_CONFLICT","message":"记录已变化，请重新查询","requestId":"本次请求编号"}
```

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 字段、JSON、UUID、版本、分页或状态非法 |
| 404 | CATEGORY_NOT_FOUND / ARTICLE_NOT_FOUND / SESSION_NOT_FOUND | 目标不存在；文章引用不存在分类 |
| 409 | VERSION_CONFLICT | 更新或删除版本过期 |
| 409 | DATA_CONFLICT | 删除被引用分类或其他数据约束冲突 |
| 503 | DATABASE_UNAVAILABLE | 数据库连接异常 |
| 500 | DATABASE_ERROR | 其他数据库操作失败 |

无身份认证和归属隔离，不用于公网或真实多用户场景。JWT与客户/商家权限在V3验收。

## 旧接口兼容

原/api/v1/consultations的创建、详情、历史、/draft生成、/review人工确认，以及/api/v1/germination-drafts预览保留原JSON。旧生成需Python且可返回502/503/504，当前三个传统模块无需Python。新会话CRUD不隐式调用旧草稿接口。
