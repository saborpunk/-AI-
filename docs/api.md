# API说明

[项目入口](../README.md) · 基址 `http://127.0.0.1:8080/api/v1` · JSON请求 · 当前本机开发；除注册、登录和GET /actuator/health外均需JWT。

## 认证与权限

| 方法/路径 | 输入 | 返回 |
| --- | --- | --- |
| POST /auth/register | username、password、displayName | 201，用户信息；固定CUSTOMER，无密码字段，Location=/api/v1/users/me |
| POST /auth/login | username、password | 200，accessToken、tokenType=Bearer、expiresIn=900（秒） |
| GET /users/me | Authorization头 | 200，id/username/displayName/role/status/version/createdAt |

注册用户名去首尾空格转小写，3—32位字母数字下划线；密码12—72个Java字符且UTF-8不超过72字节，昵称非空且最多80字符。注册不允许选择商家角色。重复用户名409/USERNAME_EXISTS，校验失败400；登录失败401/BAD_CREDENTIALS。

后续请求头：`Authorization: Bearer <登录返回的accessToken>`。不在URL、Cookie或日志传令牌。不支持自动刷新；Token过期后重新登录。前端退出仅清理页面凭证，不撤销已复制的Token。

| 资源 | 客户 | 商家 |
| --- | --- | --- |
| /users/me | 当前本人 | 当前本人 |
| /sessions及子路径 | 仅本人会话 | 全部会话，含历史未归属 |
| /articles、/article-categories | 403 | 全部CRUD |
| /consultations、/germination-drafts旧接口 | 403 | 兼容原业务 |

没有/无效/过期Token或账号被禁用：401/UNAUTHORIZED；角色不足：403/FORBIDDEN；客户访问别人的或未归属会话：404/SESSION_NOT_FOUND。前端不能提交userId认领会话，创建时由认证上下文决定。账号禁用期间旧Token也拒绝；认证数据库故障503/DATABASE_UNAVAILABLE。

V3保留原CRUD的URL、JSON字段、Location和业务状态码，新增认证前置要求。会话响应不暴露内部user_id。所有响应有X-Request-Id；认证错误同样使用下方错误结构。

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

完整用户信息修改、用户状态管理页面和客户知识文章浏览留到V4。当前仅监听127.0.0.1。

## 旧接口兼容

原/api/v1/consultations的创建、详情、历史、/draft生成、/review人工确认，以及/api/v1/germination-drafts预览保留原JSON。旧接口须商家Bearer Token；旧生成需Python且可返回502/503/504，当前三个传统模块无需Python。新会话CRUD不隐式调用旧草稿接口。
