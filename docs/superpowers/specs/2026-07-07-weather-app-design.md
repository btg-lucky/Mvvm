# module_weather 天气功能设计

日期：2026-07-07
状态：设计已确认，待写实现计划

## 背景与目标

`module_weather` 目前是占位（单 Fragment 显示「开发中」）。本设计把它落地为真实天气功能，数据源用**聚合数据「天气预报」接口**，对齐 `module_news` 的分层与实现模式（数据源接口 + Remote/Fake 双实现、Repository 唯一入口、Hilt 装配、`safeApiCall`/`ApiResult`/`UiState`/`StateLayout` 四态）。

## 数据来源（聚合数据）

baseUrl：`https://apis.juhe.cn/`（**与 news 的 `https://v.juhe.cn/` 不同**）。统一响应外壳 `{ error_code, reason, result }`，`error_code == 0` 为成功。

用到两个接口，第三个内置：

1. **根据城市查询天气** `GET simpleWeather/query`
   - 参数：`city`（城市名/ID，UTF-8 urlencode）、`key`
   - `result.realtime`：`info`(天气文案)、`wid`、`temperature`、`humidity`、`direct`(风向)、`power`(风力)、`aqi`（均可能为空）
   - `result.future`：近 5 天数组，每项 `date`、`temperature`("最低/最高℃")、`weather`、`wid`(对象 `{day,night}`)、`direct`
2. **根据城市查询生活指数** `GET simpleWeather/life`
   - 参数：`city`、`key`
   - `result.life`：`kongtiao/guomin/shushidu/chuanyi/diaoyu/ganmao/ziwaixian/xiche/yundong/daisan` 共 10 项，每项 `{ v(档位), des(详情) }`，有时可能为空
3. **天气种类列表** `GET simpleWeather/wids`——**不调用**。`wid → 天气名` 的 30+ 项映射固定，直接内置成常量（文档明确「可以程序内置，无需每次读取」）。

错误码：业务级如 `207301 错误的查询城市名`、`207302 查询不到该城市信息`、`207303 网络错误`；系统级 `10001~10021`（key/权限/限流等）。均经 `JuheResponse.unwrap()` 抛 `AppException.Business(code, reason)`，UI 显示 reason 友好文案。

## 已确认的产品决策

- **城市选择**：默认「杭州」，顶部城市名可点，弹输入框切换城市（方案 4）。
- **展示内容**：query（实况 + 近 5 天）+ life（生活指数）。
- **首页背景随天气变化**：渐变背景 + 简单矢量插画（方案 2 的占位形态）。
  - 不使用实拍图（无图片生成能力，网图有版权/失效风险）。
  - 背景资源做成「天气档位 → 资源」结构，先用 `GradientDrawable` 渐变 + `VectorDrawable` 矢量插画（太阳/云/雨滴/雪花/雾，纯代码零版权）；日后若换实拍图，只替换 drawable，不动逻辑。
- **JuheResponse 复用**：方案 A——在 weather 模块内复制一份 `JuheResponse` + `unwrap()`，保持两个 feature 模块互相独立、互不依赖（代价：约 15 行重复）。

## 整体形态

单页 `WeatherFragment`（`ScrollView` 承载），一屏四段：

1. **顶部栏**：城市名（可点 → 弹输入框切换城市）+ 刷新入口。
2. **当前天气卡**：铺在随天气变化的渐变背景 + 矢量插画上——大字号温度、天气文案、湿度/风向/风力/AQI。
3. **近 5 天预报**：横向 `RecyclerView`（日期 / 小插画 / 温度区间 / 天气 / 风向）。
4. **生活指数**：网格 `RecyclerView`（10 项，显示名称 + 档位 `v`），点某项弹出详情 `des`（用 lib_common 的 `showAlertDialog`）。

状态：`StateLayout` 四态（Loading/Content/Empty/Error）+ `SwipeRefreshLayout` 下拉刷新，沿用 news 模式。

## 分层与文件规划（`com.btg.weather`）

### data/model
- `WeatherOverview`：`realtime: RealtimeWeather`、`future: List<ForecastDay>`、`life: List<LifeIndex>`、`city: String`。
- `RealtimeWeather`：`info`、`category: WeatherCategory`、`temperature`、`humidity`、`direct`、`power`、`aqi`。
- `ForecastDay`：`date`、`temperature`、`weather`、`category: WeatherCategory`、`direct`。
- `LifeIndex`：`name`(展示名，如「穿衣」)、`level: String`(v)、`desc: String`(des)。
- `WeatherCategory`（enum）：`CLEAR / CLOUDY / RAIN / SNOW / FOG` 五档；每档持有渐变背景资源 id 与矢量插画资源 id；伴生 `fromWid(wid: String?): WeatherCategory` 纯函数做 `wid → 档位` 的 `when` 映射（内置常量）。

wid 归档（依据接口天气种类表）：
- `CLEAR`：`00`（晴）
- `CLOUDY`：`01`(多云)、`02`(阴)
- `RAIN`：`03,04,05`(阵雨/雷阵雨/冰雹)、`07~12`(小雨~特大暴雨)、`19`(冻雨)、`21~25`(混合雨)
- `SNOW`：`06`(雨夹雪)、`13~17`(阵雪~暴雪)、`26~28`(混合雪)
- `FOG`：`18`(雾)、`20`(沙尘暴)、`29`(浮尘)、`30`(扬沙)、`31`(强沙尘暴)、`53`(霾)
- 未知 wid → 兜底 `CLOUDY`

### data/source
- `WeatherApi`：Retrofit suspend 接口，`query(key, city)`、`life(key, city)`，返回 `JuheResponse<...>`。
- `JuheResponse.kt`：本模块内自有的 `JuheResponse<T>(error_code, reason, result)` + `unwrap()`（方案 A，复用 lib_common 的 `AppException`）。
- `WeatherDto.kt`：query/life 的 DTO + `toModel()` 映射（含空值兜底 `.orEmpty()`）。life 的 10 个键 → `LifeIndex` 列表（键到中文名的映射在此）。
- `WeatherDataSource`（接口）：`fetchWeather(city): WeatherData`、`fetchLife(city): List<LifeIndex>`（失败抛异常，Repository 兜）。其中 `WeatherData(city, realtime, future)` 是数据源层的小载体，`city` 取接口 `result.city`（可能被服务端规范化），`realtime: RealtimeWeather`、`future: List<ForecastDay>`。
- `RemoteWeatherDataSource`：真实网络，`api.query(...).unwrap()` → 映射；`api.life(...).unwrap()` → 映射。
- `FakeWeatherDataSource`：假数据 + `delay`，无 key/单测用。

### data/repository
- `WeatherRepository(dataSource, ioDispatcher = Dispatchers.IO)`：
  - `getWeatherOverview(city): ApiResult<WeatherOverview>` —— `withContext(io) { safeApiCall { ... } }`：先 `fetchWeather`（必需，得到 `WeatherData`），成功后 `runCatching { fetchLife }`（**次要，失败吞成空列表**，不阻塞主天气），合并为 `WeatherOverview`（`city` 取 `WeatherData.city`）。

### di/WeatherModule.kt
- `@WeatherJuheRetrofit` Qualifier（本模块专用，baseUrl `https://apis.juhe.cn/`）。
- `@Provides` 独立 Retrofit（复用 `NetworkModule` 注入的 `OkHttpClient` + `Gson`）→ `WeatherApi` → `WeatherDataSource`（默认 `RemoteWeatherDataSource(api, BuildConfig.JUHE_API_KEY)`）→ `WeatherRepository`。
- `CityStore`：用 lib_common `PreferenceStore`(DataStore) 存选定城市，也在此装配。

### ui
- `WeatherUiState`：`city: String`、`content: UiState<WeatherOverview>`、`isRefreshing: Boolean`。
- `WeatherViewModel`(`@HiltViewModel`)：`init` 读 `CityStore`（无则「杭州」）→ 加载；`selectCity(name)`（存 `CityStore` + 重新加载）、`refresh()`。暴露 `StateFlow<WeatherUiState>`，错误经 `errorEvent`。
- `WeatherFragment`(`@AndroidEntryPoint`)：渲染四态；顶部城市点按弹 `AlertDialog`（内含 `EditText`）切城市；按 `realtime.category` 设背景渐变 + 插画。
- `ForecastAdapter` / `LifeAdapter`：`ListAdapter + DiffUtil`；`LifeAdapter` 项点击回调 → Fragment 弹 `showAlertDialog(des)`。

### res
- `layout/fragment_weather.xml`：重写占位布局（ScrollView + 顶部栏 + 天气卡 + 两个 RecyclerView，外套 `StateLayout` + `SwipeRefreshLayout`）。
- `layout/item_forecast.xml`、`layout/item_life.xml`。
- `drawable/`：5 档渐变背景 `bg_weather_*.xml`（`<shape>` gradient）+ 5 个矢量插画 `ic_weather_*.xml`（`<vector>`：太阳/云/雨滴/雪花/雾）。
- `values/strings.xml`：城市切换、生活指数 10 项中文名、错误/空态文案等（不硬编码）。

### build.gradle.kts（module_weather）
补：`alias(libs.plugins.hilt)`、`alias(libs.plugins.ksp)`；`implementation(libs.hilt.android)` + `ksp(libs.hilt.compiler)`；`buildFeatures { viewBinding = true; buildConfig = true }`；`defaultConfig { buildConfigField("String", "JUHE_API_KEY", "\"$juheApiKey\"") }`（从 `rootProject.file("local.properties")` 读 `JUHE_API_KEY`，与 news 一致）；`testImplementation(libs.junit)` + `libs.kotlinx.coroutines.test`。**不新增第三方依赖、不改 SDK 版本、不动权限。**

## 导航集成

无需改 app 壳：`nav_weather.xml` 已被 `nav_main.xml` include，底部菜单 `nav_weather` tab 已关联 graph `@id/nav_weather`，起始 `weatherFragment` 不变（仅替换其内容）。生活指数详情走弹窗，不新增 destination。

## 测试

- `WeatherCategory.fromWid()`：纯函数单测（各档位边界 wid、未知 wid 兜底）。
- `WeatherRepository`：`FakeWeatherDataSource` + `runTest` + 注入 dispatcher；覆盖「life 失败但天气成功仍返回 Success（life 空）」。
- `WeatherViewModel`：`MainDispatcherRule`（`UnconfinedTestDispatcher`，仿 news/mine，各模块自带一份）；覆盖切城市、刷新、错误下发。
- 依赖 framework 的（DataStore、AlertDialog、背景渲染）靠手动验证。不引 mockk/Robolectric/Turbine。

## 明确不做（YAGNI）

- 不调 `wids` 接口（内置映射）。
- 不做定位/逆地理编码（城市靠默认 + 手动输入）。
- 不做城市搜索联想/城市列表选择（纯输入框切换）。
- 不加实拍背景图（矢量插画占位，结构预留可替换）。
- 不加天气详情二级页（生活指数详情用弹窗）。
