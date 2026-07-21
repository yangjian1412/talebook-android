# 开发计划

### 1. 全屏阅读模式
**目标**：阅读页面支持全屏，隐藏顶栏和系统导航栏，退出时完整恢复。

**技术方案**：
- 详情页加一个「全屏阅读」按钮，绕开所有切换重排版问题
- 阅读页面（`ReaderScreen.kt`）根据 `isFullscreen: Boolean` 参数走两条不同的渲染分支：
  - `false`（原"在线阅读"）：`Scaffold + TopAppBar`，书名顶栏显示
  - `true`（"全屏阅读"）：无顶栏，`controller.hide(systemBars())` 隐藏状态栏+导航栏，左上角浮动半透黑圆返回按钮
- 用 `WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`，从屏幕边缘上滑临时拉出系统栏
- 退出页面 `onDispose { controller.show(...) }` 恢复

**踩过的坑**：
- `enableEdgeToEdge()` 内部设置 `setDecorFitsSystemWindows(false)`，之后手动调用 `show()` 无法恢复导航栏
- `setDecorFitsSystemWindows` 切换会导致 WebView 布局变化 → `onPageFinished` 触发 → 被重定向逻辑误判为登录跳转
- Scaffold TopAppBar 显隐会导致 padding 变化 → WebView 尺寸变化 → 阅读位置丢失
- 同时切换这两个开关是 PLAN 里 #3（运行时 toggle）失败的根因

**状态**：✅ 已实现（方案①：详情页选模式 + ReaderScreen isFullscreen 参数）

---

### 2. 下载功能
**目标**：图书详情页提供下载按钮，将电子书下载到本地。

**技术方案**：
- 使用 OkHttp 直接下载（带 `CookieJar`，复用登录 cookie）
- API 端点：`GET /api/book/{id}.{ext}`（如 `/api/book/123.epub`）
- 保存路径：`/storage/emulated/0/Download/talebook/{书名}.{ext}`
  - API ≥ 29：走 `MediaStore.Downloads.EXTERNAL_CONTENT_URI` + `RELATIVE_PATH=Download/talebook/`，`IS_PENDING` 标记事务，无需运行时权限
  - API 26-28：走 `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)/talebook/`，需要 `WRITE_EXTERNAL_STORAGE`（manifest 已加 `maxSdkVersion=28`）
- 格式选择 `pickFormat()`：先看 `available_formats`（逗号分隔字符串拆开），再看 `fmt_epub / fmt_pdf / fmt_azw3 / fmt_mobi` 字段，优先级 EPUB > PDF > AZW3 > MOBI
- 文件名 sanitize：去掉 `\\/:*?"<>|` 等非法字符，截 120 字
- UI：详情页加 OutlinedButton 按钮，状态机 `Idle / Downloading(downloaded, total) / Done / Failed`；失败信息带诊断字段值

**已知限制**：
- 服务器配置 `allow.download=false`，下载会被拒绝（返回 403），按钮显示「下载失败: 服务器未启用下载权限 (HTTP 403)」
- 需要管理员在服务器端开启 `allow.download` 权限

**踩过的坑**：
- 首次实现只看了 `fmt_*` 单字段，导致部分书被误判为"无可下载格式"。talebook 服务端其实更倾向用 `available_formats` 逗号分隔字符串作为单一字段，`pickFormat()` 现已两个来源都查

**状态**：✅ 已实现，待服务器端开启 `allow.download` 后能正常下载

---

### 3. 书架功能
**目标**：图书详情页支持加入/移出书架，首页展示书架图书。

**技术方案**：
- 获取书架：`GET /api/shelf` → 返回 `{books: [...]}`
- 加入书架：`POST /api/book/{id}/shelf` + `@Field("shelf") true`
- 移出书架：`POST /api/book/{id}/shelf` + `@Field("shelf") false`
- 首页新增「我的书架」行（在「最近阅读」和「随机推荐」之间）

**踩过的坑**：
- talebook 书架 API 不是 `/api/book/{id}/bookshelf/add`，正确路径是 `/api/book/{id}/shelf`
- POST 请求 body 必须用 `@FormUrlEncoded` + `@Field`，不能用空 body
- `@POST` 不加 `@FormUrlEncoded` 且无 `@Field` 会报错 "form encoded method must contain at least one @field"

**状态**：✅ 已实现。详情页「加入书架 / 移出书架」按钮（`BookDetailScreen`），首页新增「我的书架」行（`HomeScreen`，在「最近阅读」和「随机推荐」之间），登录后可用。

---

### 4. 阅读器 UI 调整 + 阅读时长统计 + 打卡 streak
**目标**：在不替换 WebView 的前提下，把 ReaderScreen 体验拉近原生。具体：
- 给外壳加底部工具条（Compose）：目录 / 字号 / 夜间模式 / 跳转页码
- 阅读时长本地统计（按天累计）
- 每日打卡 streak（首页 / 设置可见）
- 快速续读：上次读到的位置直接进入

**Level 1（轻改版，本次目标）技术方案**：
- **`ReaderScreen.kt`**：
  - 不动 WebView 内部（talebook 阅读器还是服务端响应）
  - 顶层 `Box(Modifier.fillMaxSize())` 内部分两块：
    - WebView（同当前实现）
    - 底部工具条 `BottomToolbar`：4 个 IconButton（目录 / 字号 / 夜间 / 跳转）
    - 中间 `TapToToggleOverlay`：点屏幕中央唤出/隐藏工具条（用 `pointerInput` 监听点击，2 秒后自动隐藏）
    - 左上角返回按钮维持现有（仅全屏模式）
- **`ReadingTimer.kt`**（新文件，object）：
  - 包 `DataStore<Preferences>`，key: `total_seconds_{yyyy-MM-dd}`、`streak_days`、`last_streak_date`、`continuous_streak`
  - API：`startSession()` / `stopSession()`，内部用 coroutine 计时
  - 累计阈值 ≥ 30 秒当天打卡，更新 streak
- **`ReaderViewModel.kt`**：
  - 进入屏幕 `onStart()` 调用 `ReadingTimer.startSession()`
  - 离开屏幕 `onStop()` 调用 `stopSession()`，把本次秒数入库
  - 把当天已读秒数和 streak 通过 UI State 暴露（给屏幕可选显示）
- **`BookDetailScreen.kt`**：
  - 加个轻量的"今日已读 X 分钟"小灰字（用 `BookDetailViewModel` 里的 reading state，新拉一次）
- **`HomeScreen.kt`**：
  - 在"最近浏览"区下方加一行 "📅 已连续打卡 N 天 · 今日 X 分钟"
- **`SettingsScreen.kt`**：
  - 显示总打卡天数 / 累计阅读时长，可选重置

**Level 2（中改，待 #4 完成后再决定要不要做）**：
- 给 WebView 加 `@JavascriptInterface`：`TaleBookBridge` 提供 `setChapterName(name)`/`setProgress(cfi, percentage)`/`setPageCount(n)`
- talebook 的阅读器 JS 里嵌入一个 `interface.js` 注入，向上汇报位置
- ReaderScreen 顶部能显示真章节名；打卡时上报带 CFI 到服务端

**Level 3（大改，替换 WebView）**：
- 引入 folioreader / Readium SDK 做 EPUB 原生渲染，章节翻页 0 延迟
- 划线 / 笔记 / 同步到 `/api/book/{id}/readstate`
- PDF 用系统 `PdfRenderer`；AZW3/MOBI 引导用户换 EPUB
- 不在本期范围内

**踩过的坑（预计）**：
- 阅读计时需要在 `DisposableEffect` 内 `onStart`/`onStop`，不能用 `LaunchedEffect`，否则屏幕旋转 / 系统返回会让计时错乱
- 章节跳转如果走 Level 1 占位，可以先不做（让用户继续用 talebook 的内置目录）
- 字号、夜间模式严格说需要向 WebView 内 JS 注入 CSS / 调 API。Level 1 如果时间紧，可以先把这两个按钮做成"本地偏好"占位（按钮显示选中状态，但实际效果依赖 Level 2 的 JS 桥才能生效）

**状态**：⏳ 待开发（Level 1 轻改版优先）

---

### 5. 阅读器夜间模式真正触发 talebook 自家系统
**问题描述**：app 主题虽然切到了夜间，WebView 视觉上看着也黑了，但 talebook 自带的 Vue/Nuxt 主题系统仍然处于 light 状态。表现为：
- talebook 自己的 toolbar "夜间/白天"切换按钮没有跟着翻
- 它内部的章节色、字号算法、highlight 颜色都还是按 light 配的
- 用户的阅读位置 / 笔记 / 划线颜色等可能因此和"以为是夜间"的状态不一致

**已经尝试过的注入，全部不奏效**（详见 `ReaderScreen.kt` 的 `DARK_MODE_JS`）：
- `document.documentElement.setAttribute('data-theme','dark')` + `classList.add('dark')` + `style.colorScheme='dark'`
- `localStorage.setItem('nuxt-color-mode','dark')` / `vueuse-color-scheme` / `talebook.theme` 等通用 key
- 扫描页面上文案 / `aria-label` 匹配 `夜间|night|dark|暗黑|暗色` 的按钮并 `.click()`
- CSS overlay 兜底（只解决视觉）

**根因猜测**：talebook 不是用通用 `@vueuse/core` 的 `useDark` / Nuxt `useColorMode`，而是有自己的主题 store。三种可能触发方式：
1. 服务端有个 `/api/user/prefs` 之类的偏好接口，POST 一次把它写进服务端
2. talebook 的 SPA 里某个内置 Pinia store，要通过 JS 桥按它家的字段名写值
3. 干脆放弃触发它家，自己用 Level 3（folioreader / Readium）替换掉 WebView

**当前行为**：app 切到夜间后，`DARK_MODE_JS` 会跑一次。结果是 → talebook 内部 light、我们 overlay deep gray，二者叠加看起来像夜间但点击 toolbar 上的按钮 (例如字号) 弹的弹窗还是白色。

**待选修法**（按推荐顺序）：
1. 找服务端 `/api/user/prefs` 或类似 prefs 接口（curl 一遍 `https://book.liufenyi.xyz:9973/api/user/info` 之类看返回）；若有就 POST 改 dark，再加载阅读器
2. 在 talebook 的 SPA 里远程调试找它主题 store 的字段名，通过 JS 桥写入
3. 实在不行：CSS overlay 范围扩大，让视觉完全黑（不影响功能，只是 talebook 内部逻辑仍按 light 跑）
4. 终极：Level 3 替换 WebView

**状态**：⏳ 待开发（用户已确认 talebook 自家 dark 没成功触发，仍是 TBD）

---

### 6. 朗读功能 (TTS)
**目标**：阅读器增加文字朗读（TTS），方便开车 / 做饭 / 健身时听书。

**待选实现路径**（视 §4/§5 进展而定）：

**路径 A：Android `TextToSpeech` 引擎直接读**（强依赖 §4 Level 3，跳过 WebView）
- 用 folioreader / Readium SDK 的 Spine 解析，把当前章节纯文本取出（不含 HTML / CSS）
- `TextToSpeech.speak(text, QUEUE_FLUSH, null, utteranceId)`
- 系统自带的 TTS 引擎（Google / 厂商）负责读
- UI：ReaderScreen 底部加一个播放条：▶/⏸/⏹、语速滑块（`tts.setSpeechRate`）、上一段/下一段
- 服务端接口 / 字段检查后尚不知：talebook 是否会把章节纯文本 embed 在章节 HTML 里（一般会，可以 `document.body.innerText` 提取）

**路径 B：WebView JS 桥接**（依赖 §4 Level 2）
- 通过 `@JavascriptInterface` 让 talebook 阅读器的 JS 帮我们提取章节纯文本
- 然后 Android TTS 引擎来读
- 优点：少一步流式包装；缺点：受限于 talebook 内已加载的章节上下文

**待定项**：
- TTS 引擎选择（系统默认？还是让用户在设置里选？）
- 后台播放 vs 仅前台（前台简单；要后台播放得走 `Service` + `MediaSession`，非前台 App 也能在通知中心控制）
- 段落高亮：当前正在朗读的字 / 句在 WebView 里高亮吗？需要 §4 Level 2 拿到 CFI / 章节坐标后才能做
- 多语言：英文用户希望用英文 TTS，中文用中文。看 talebook 的书库是不是混排的

**踩坑预计**：
- 系统 TTS 引擎差异巨大（小米 / 三星 / 原生 Pixel 都装的不一样），要测机兼容性
- 后台播放要 `MediaSession` + `FOREGROUND_SERVICE` 权限

**状态**：⏳ 待开发（路径 B 更稳，能复用 §4 的 JS 桥；纯本地路径 A 等到 Level 3 再上）

---

### 7. 书签功能
**目标**：用户在阅读时给任意位置打一个书签；能列出来、跳转、改名、删除；与 talebook 服务端已有书签（若 API 暴露）打通或纯本地。

**功能细分**：
- **打书签**：阅读器工具条加 🔖 按钮，点一下给当前阅读位置打个标。可输入一段文字作为备注
- **列书签**：详情页或独立页面列出本书所有书签（按章节位置 + 时间）
- **跳书签**：点一条 → 直接跳到那个位置继续读
- **改 / 删**：长按书签弹菜单
- **同步到服务端（待评估）**：
  - 如果 talebook 服务端有书签 API（看到 `/api/book/{id}/bookmark` 之类的）：打通，让书签跨设备同步
  - 没有就纯本地存

**技术方案**：
- 本地存储：DataStore + JSON 序列化（或升 Room；如果书签多了再考虑）
- 数据：`{ bookId, title, chapter?, cfRange?, percentage, note, color?, createdAt }`
- UI：
  - ReaderScreen 工具条内放一个 🔖 按钮（依赖 §4 Level 1 工具条先做出来）
  - 独立页面 `BookmarksScreen`，路由 `bookmarks/{bookId}`；菜单 / 详情页入口
  - 长按弹 `AlertDialog` 操作
- 阅读器定位：用 §4 Level 2 的 JS 桥拿到 `cfi` / 百分位，跳转时调 talebook 阅读器的 JS 函数 `book.goto(cfi)`

**踩坑预计**：
- 若 §4 Level 2 JS 桥未做，"当前章节 / CFI"是空的，会退化到只存 `percentage`，精度只能到页
- 服务端书签 API 路径要先 curl 探测；talebook 旧版本没书签 API 也别卡
- 删除 / 重命名要单独调 API；失败要回滚本地缓存

**状态**：⏳ 待开发（顺手排在 §4 Level 1 工具条之后，与 §4 Level 2 几乎绑定）

---



