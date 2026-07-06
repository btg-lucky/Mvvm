# 新闻 App 模块化改造设计

日期:2026-07-06
状态:已确认

## 背景与目标

基于现有 MVVM 脚手架,把演示台工程改造为一个真实的三模块 App:

1. **新闻**:分类列表 → 点击进入详情,数据来自聚合数据「新闻头条」接口
2. **天气**:占位空白页(暂不开发)
3. **我的**:注册 / 登录,基于本地 Room 数据库

整个工程采用**业务模块化**:每个业务是独立 Gradle library module,app 退化为壳。

## 模块结构与依赖方向

```
app(壳:MainActivity + BottomNavigationView + 导航装配,@HiltAndroidApp)
 ├── module_news     新闻(列表 + 分类 Tab + 详情)
 ├── module_weather  天气(占位页)
 ├── module_mine     我的(登录/注册/用户页,Room 用户库)
 └── lib_common(框架核心)──api──▶ lib_opensource / lib_widget
```

- 依赖方向:`app → 3 个 feature module → lib_common`;三个 feature **互不依赖**。
- feature module 的 namespace:`com.btg.news` / `com.btg.weather` / `com.btg.mine`。
- app 与三个 feature、lib_common 应用 Hilt + KSP;`module_mine` 额外应用 Room KSP。
- 版本与依赖别名沿用 `gradle/libs.versions.toml`,新模块脚本用 Kotlin DSL。

## 导航方案

- 单 Activity 不变。`activity_main` = `NavHostFragment` + `BottomNavigationView` 三 Tab(新闻 / 天气 / 我的)。
- **每个 feature 自带自己的 navigation graph**:
  - `module_news/nav_news.xml`:列表页(startDestination)→ 详情页
  - `module_weather/nav_weather.xml`:占位页
  - `module_mine/nav_mine.xml`:我的页(startDestination)→ 登录页 → 注册页
- app 的主 graph `nav_main.xml` `<include>` 三个子 graph;BottomNav menu item id 与各子 graph id 一致,`setupWithNavController` 自动接管,Tab 切换保留各自返回栈(Navigation 多返回栈)。
- 现有演示页(HomeFragment、ComponentsFragment、StorageFragment、Room 收藏 demo)**删除**;app 里现有的新闻演示代码(`data/`、`ui/news/`)**迁入 `module_news`** 并补全真实接口。

## module_news(新闻)

### 接口(聚合数据·新闻头条)

| 接口 | 地址 | 参数 | 返回 |
|---|---|---|---|
| 列表 | `GET https://v.juhe.cn/toutiao/index` | `key`、`type`(top/guonei/guoji/yule/tiyu/keji/caijing/youxi/qiche/jiankang)、`page`(默认1,≤50)、`page_size`(默认30,≤30)、`is_filter=1`(只要有详情的新闻) | `result.data[]`:uniquekey/title/date/category/author_name/url/thumbnail_pic_s/is_content |
| 详情 | `GET https://v.juhe.cn/toutiao/content` | `key`、`uniquekey` | `result.detail`(title/date/category/author_name/url/thumbnail_pic_s)+ `result.content`(HTML 字符串) |

- 响应外壳:`{ error_code: Int, reason: String, result: T? }`,`error_code == 0` 为成功。需在 `module_news` 增加对应的 `JuheResponse<T>` + `unwrap()`(成功码 0,失败抛 `AppException.Business(error_code, reason)`),接入现有 `safeApiCall` → `ApiResult` 体系。
- 服务级错误码:`223502`(暂查询不到详情)→ 详情页降级;系统级 `10011/10012/10013`(频次限制)→ 友好文案提示。
- baseUrl:`https://v.juhe.cn/`,与 `lib_common` 的 `BASE_URL` 不同,用 Hilt `@Qualifier` 提供独立 Retrofit 实例(复用 NetworkModule 的 OkHttpClient/Gson)。

### API key

- `local.properties` 中配置 `JUHE_API_KEY=xxx`(该文件已被 gitignore,不进版本库)。
- `module_news/build.gradle.kts` 读取并注入 `BuildConfig.JUHE_API_KEY`;未配置时注入空串,请求返回错误由 StateLayout 错误态呈现。

### 页面

**列表页 `NewsListFragment`**
- 顶部 `TabLayout`(可滚动)承载 10 个分类;切换分类重置 page=1 重新请求。
- `SwipeRefreshLayout` 下拉刷新 + `RecyclerView`(ListAdapter + DiffUtil,Coil 加载缩略图)+ `RecyclerView.onLoadMore` 上拉加载下一页(page ≤ 50 或返回空视为到底)。
- `StateLayout` 四态渲染 `UiState`(Loading/Success/Empty/Error,错误态可重试)。
- 点击条目携带 `uniquekey` + `title` + `url`(降级用)通过 Bundle 导航到详情页(工程未引 Safe Args 插件,不新增)。

**详情页 `NewsDetailFragment`**
- 上半部原生展示:标题、分类·来源、时间。
- 下半部 `WebView.loadDataWithBaseURL` 渲染 `content` HTML(注入 `<meta viewport>` + `img{max-width:100%}` 简单样式;content 中图片多为 `//` 协议相对地址,base 用 `https:`)。
- 详情接口失败(如 223502)时降级:WebView 直接 `loadUrl(原文 url)`。
- 生命周期:`onDestroyView` 销毁 WebView,避免泄漏。

### 数据层

- `NewsDataSource`(接口)/ `RemoteNewsDataSource`(真实实现)/ `FakeNewsDataSource`(保留,测试与无 key 演示用)。
- `NewsRepository`:`getNews(type, page): ApiResult<List<NewsItem>>`、`getNewsDetail(uniquekey): ApiResult<NewsDetail>`,`withContext(ioDispatcher)`,dispatcher 可注入。
- Hilt 模块 `NewsModule` 装配;换数据源只改 `@Provides` 一行。

## module_mine(我的)

### 数据层

- Room:`UserEntity(id 自增, username 唯一索引, passwordHash, salt, createdAt)` + `UserDao(: BaseDao)` + 模块内 `MineDatabase`,Hilt 提供单例。
- 密码安全:注册时生成随机盐,存 `SHA-256(salt + password)`;校验逻辑抽纯函数 `PasswordHasher`(可单测),不依赖 Android framework。
- 登录态:`PreferenceStore`(DataStore)存当前登录用户名;`Flow` 暴露,冷启动自动恢复。
- `UserRepository`:`register(username, password): ApiResult<Unit>`(用户名已存在返回 Business 错误)、`login(username, password): ApiResult<Unit>`、`logout()`、`currentUser: Flow<String?>`。

### 页面

- **我的页 `MineFragment`**:观察 `currentUser`;未登录 → 显示"登录 / 注册"入口;已登录 → 显示用户名 + 退出登录按钮(清 DataStore)。
- **登录页 `LoginFragment`**:用户名 + 密码,校验失败 toast 提示;成功后写入登录态并返回我的页。提供跳注册入口。
- **注册页 `RegisterFragment`**:用户名 + 密码 + 确认密码;本地校验(非空、两次一致、长度下限)+ 用户名重复校验;成功后自动登录并返回我的页。

## module_weather(天气)

- `WeatherFragment` 占位:居中提示"天气功能开发中"。自带 `nav_weather.xml`,后续开发不影响其他模块。

## 错误处理

- 网络/超时/解析:沿用 `ExceptionHandler` → `AppException` → 友好文案;列表页错误态 StateLayout 可重试,详情页错误降级原文 url。
- 一次性事件(toast、导航)用 SharedFlow/Channel 事件,不用 LiveData。

## 测试

沿用现有约定(`kotlinx-coroutines-test` + 手写 fake,不引 mock 框架):

| 被测对象 | 方式 |
|---|---|
| `JuheResponse.unwrap` | 纯 JVM 单测(成功/业务错误/result 为 null) |
| `NewsRepository` | fake `NewsDataSource` + `runTest` |
| `NewsListViewModel` | fake Repository + `MainDispatcherRule`(分类切换/分页/刷新/错误) |
| `PasswordHasher` | 纯函数单测(同盐同密码一致、不同盐不同) |
| `UserRepository` | fake `UserDao` + fake DataStore(注册重名/登录成/败/登出) |
| UI(WebView、BottomNav 等) | 手动验证,不写 instrumented 测试 |

## 明确不做(YAGNI)

- 模块间路由/服务接口层(feature 间无跳转需求)
- 新闻收藏、搜索、缓存落库
- 天气模块任何真实功能
- 登录 token/会话过期(纯本地账号体系)
