# Development Notes

本文档面向维护者和贡献者，说明 Talebook Android 的源码结构、阅读器架构、关键数据流和常见修改入口。项目发布说明请看 `README.md`。

## 项目目标

Talebook Android v2.0 的核心方向是把 App 做成完整的本地阅读入口：

- talebook 服务端负责登录、书籍元数据和书籍文件资源。
- Android App 负责阅读体验、缓存、进度、书签、搜索、笔记和 TTS。
- 本地 Readium 阅读器是唯一阅读器，在线 WebView 阅读器已在 2.2.0 移除。

## 重要约定

- 本地阅读器不要强制先缓存再打开。无缓存时应流式阅读远程资源，有缓存时优先打开本地文件。
- 阅读数据当前只存本地，不做账号级多端同步。
- 跨设备迁移通过导出/导入阅读数据实现。
- TTS 已接入系统 TextToSpeech，支持阅读页内朗读和锁屏播放（2.2.2alpha 起通过 Foreground Service + WakeLock 实现，无通知栏媒体控件）。
- PDF 专项适配独立处理，不要假设 EPUB 的所有能力能直接复用到 PDF。

## 2.2.0 完成摘要

> 当前版本：`2.2.0` (versionCode = 4)
> 状态：已完成
> 目标：去掉在线阅读器、增强阅读沉浸式体验、加快主页启动、引入本地书架和 TXT 阅读。

### 目标摘要

- App 启动即全局隐藏导航栏；状态栏默认保留；阅读内可在高级设置里再隐藏状态栏，并左下进度条增加时间显示。
- 阅读工具栏改为覆盖式悬浮，显隐不修改 WebView padding / 高度，避免重排。
- 页边距拆分为左右 / 上下两个独立值。Readium 取水平，垂直通过注入 CSS 控制。
- 完全移除在线阅读器（ReaderScreen、ReaderViewModel、readerMode 设置项、路由分支）。
- 跳过验证可直接进入主页（无服务器配置也能进入），设置中保留“服务器与登录”入口供补齐。跳过验证后主页只显示本地 + 设置。
- 主页首屏显示缓存，右上角 Refresh + 下拉刷新，未配置服务器显示空态卡片。
- 新增本地书架：SAF 配置文件夹、不复制源文件、用户主动刷新、可移除、可按目录浏览；本地书与书库书在最近阅读都显示来源标签。
- 本地支持 epub / pdf / txt 阅读；mobi / azw3 / fb2 / doc 等其它格式可入库但暂不提供打开按钮（提示“暂不支持本地打开”）。
- TXT 转为缓存 EPUB 后走 Readium，支持编码识别、章节目录、缓存复用。

### 模块 A · 阅读器沉浸与体验

A1. 全局导航栏隐藏
- 修改 `app/src/main/res/values/themes.xml`，将 `Theme.Talebook` 改为无 ActionBar 全屏主题。
- `MainActivity.onCreate` 中：
  - `WindowCompat.setDecorFitsSystemWindows(window, false)`
  - `WindowInsetsControllerCompat(window, window.decorView).run { hide(Type.navigationBars()); systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }`
- `LocalReaderScreen` 不再主动恢复导航栏；`ReaderScreen` 删除后无需再处理全屏块。

A2. 阅读状态栏可隐藏开关
- 新增 `SettingsRepository.readerHideStatusBarInReader`（默认 `true`），DataStore key：`reader_hide_status_bar_in_reader`。
- 开启后 `LocalReaderScreen` 进入时 `WindowInsetsControllerCompat.hide(Type.statusBars())`；退出时由 `MainActivity` 统一策略恢复（全局导航栏仍隐藏）。
- `LocalReaderScreen` 左下角进度复合按钮增加时间显示：`HH:mm`，使用 `LocalTime.now()` 通过 `LaunchedEffect` 每分钟刷新一次；与现有进度按钮同行布局。

A3. 覆盖式悬浮工具栏
- 重写 `LocalReaderScreen` 顶层容器：
  - `AndroidView(Readium)` 占满全屏（`fillMaxSize`）。
  - 顶部 `Surface(Modifier.align(TopCenter))` 64dp；底部 `Surface(Modifier.align(BottomCenter))` 56dp；两者透明度 96% 圆角，仅通过 `alpha` 与 `translationY` 控制显隐。
  - `barsVisible` 控制悬浮层 `alpha`，不再修改 Readium padding / 高度，避免重新布局。
- 中央点击区域触发逻辑保持 `ReadiumUiEvents.centerTaps` 路径，仅新增 `barsVisible` 状态读取。
- 顶部 actions：书签、搜索、缓存、设置、退出；底部：时间 + 进度数字 + 模式切换。

A4. 页边距弹窗（左右 / 上下独立）
- `ReaderDisplaySettings` 增加 `pageMarginHorizontal: Float = 1.0f` 和 `pageMarginVertical: Float = 1.0f`，保留 `pageMargins: Float` 作为兼容过渡值，后续版本移除。
- `SettingsRepository`：
  - 新增 `READER_PAGE_MARGIN_HORIZONTAL_KEY = floatPreferencesKey("reader_page_margin_horizontal")`。
  - 新增 `READER_PAGE_MARGIN_VERTICAL_KEY = floatPreferencesKey("reader_page_margin_vertical")`。
  - 新增 `readerPageMarginHorizontal / readerPageMarginVertical: Flow<Float>`。
  - `saveReaderDisplaySettings(...)` 增加两个参数。
- 基本设置里“页边距”一行为按钮，文字形如 `页边距 左右 1.0 · 上下 1.0`，点击弹 `AlertDialog`，弹窗内两个 `CompactSlider`（0.5 – 2.0）。
- 应用规则：
  - Readium `EpubPreferences.pageMargins = settings.pageMarginHorizontal`。
  - `injectCustomCss` 追加 `html, body { padding-top: ${vH}em !important; padding-bottom: ${vV}em !important; }`（vH = vV = vertical * 0.5）。
- 旧 `reader_page_margins` 一次性迁移到新两个键（`SettingsRepository.migrateLegacyMargins()` 在 `MainActivity.onCreate` 调一次）。

### 模块 B · 删除在线阅读器

B1. 代码清理
- 删除 `app/src/main/java/com/talebook/app/ui/screens/ReaderScreen.kt`。
- 删除 `app/src/main/java/com/talebook/app/viewmodel/ReaderViewModel.kt`。
- `NavGraph.kt`：
  - 删除 `composable("reader/{bookId}?fullscreen=...")` 路由。
  - 删除 `BookDetailScreen` 的 `onReadFullscreen` 参数与 `navController.navigate("reader/...")` 调用。
  - 删除 `readerMode` Flow 收集与 `if (readerMode == ...)` 分支，统一走 `local_reader/{bookId}`。
  - 增加 `composable("local_reader/local/{localBookId}")` 路由。
- `BookDetailScreen.kt`：去掉 `readerMode` 参数、对应分支与全屏阅读按钮。
- `SettingsRepository.kt`：
  - 删除 `READER_ONLINE` 常量、`READER_MODE_KEY`、`readerMode: Flow<String>`、`saveReaderMode`。
- `SettingsScreen.kt`：删除“阅读器”分组（本地 / 在线 RadioButton）；保留“阅读器高级设置”入口。

B2. 文档 / 版本
- `app/build.gradle.kts`：`versionCode = 4`，`versionName = "2.2.0"`。
- `SettingsScreen.kt` 关于：`Tale Book v2.2.0`。
- `README.md`：`当前版本：2.2.0`，删除 alpha 措辞，新增“2.2.0 更新日志”。
- `DEVELOPMENT.md` 调试脚本 `talebook-2.1.0-debug.apk` → `talebook-2.2.0-debug.apk`。

### 模块 C · 登录 / 启动 / 主页缓存

C1. 登录页“跳过验证直接进入”
- `LoginScreen.kt`：
  - 删除“以访客身份进入（无需登录）”按钮。
  - 新增“跳过验证直接进入”按钮，文案：`跳过验证直接进入（可稍后在设置中配置服务器与登录）`。
  - `onClick = onSkipAuth`，回调 `navigate("home") popUpTo("login") inclusive=true`。
- `SettingsRepository`：
  - 新增 `SKIP_AUTH_KEY = booleanPreferencesKey("skip_auth")`。
  - `skipAuth: Flow<Boolean>`（默认 `false`）。
  - `isLoggedIn` 在 `skipAuth == true` 时永远返回 `true`。
- `NavGraph.startDestination`：若 `skipAuth || isLoggedIn` → `home`，否则 `login`。
- `MainActivity.onCreate`：读 `skipAuth`，预热一次以便首屏路径决策正确。

C2. 主页缓存
- 新增轻量本地缓存：`SharedPreferences("home_cache")` 存储上一次 `HomeUiState` 的 JSON 序列化（用 `Gson`）。
- `HomeViewModel`：
  - `cachedHome: HomeUiState?`：从缓存读取上次数据。
  - 首次进入直接 emit `cachedHome`，并设置 `isRefreshing = true`，后台拉取 `getIndex/getReading/getShelf` 后覆盖并写回缓存。
  - `forceRefresh()`：跳过缓存，立即从服务端拉取。
- `HomeScreen`：
  - 右上角 `Refresh` 按钮（`Icons.Default.Refresh`），点击 `viewModel.forceRefresh()`。
  - 使用 Material3 `PullToRefreshBox` 包裹内容，下拉同样触发 `forceRefresh()`。
  - “已缓存图书”小卡片：点击进入 `CachedBooksScreen`。

C3. 主页无服务器配置空态
- `HomeUiState` 新增 `serverConfigured: Boolean`（依据 `activeServerFromPrefs(prefs).baseUrl.isNotBlank()`）。
- `HomeViewModel`：当 `serverConfigured == false` 时不发起 `getIndex/getReading/getShelf` 请求，避免 401 噪音。
- UI 显示空态卡片：`尚未配置服务器，可进入设置添加服务器，或浏览本地书架`。

C4. 跳过验证后访问范围
- 跳过验证后主页只显示 “本地” + “设置” 两个 Tab（`homeTabs` 被强制设为 `local`）；书库 / 最近阅读入口隐藏。
- 用户进入“设置 → 服务器与登录”补齐登录后，`homeTabs` 恢复为用户之前的偏好。
- 设置“服务器与登录”：
  - 当前书库卡片：昵称 / baseUrl / 登录模式。
  - 操作：`换书库` / `登出` / `添加服务器`。

C5. 主页标签可见性
- `SettingsRepository.homeTabs: Flow<String>`，取值 `library` / `local` / `both`，默认 `both`，DataStore key `home_tabs`。
- `MainTabsScreen`：
  - 收集 `homeTabs`：
    - `library`：2 tabs，`书库` + `设置`，把“最近阅读”内容合并到“书库”顶部区块。
    - `local`：2 tabs，`本地` + `设置`。
    - `both`：4 tabs，`最近阅读` + `书库` + `本地` + `设置`。
  - 设置页“主页”分组加三态 RadioButton，写回 `homeTabs`。
- 跳过验证生效时（`skipAuth == true`），`homeTabs` 被覆盖为 `local`，不允许显示书库 tab。

### 模块 D · 本地书库

D1. 数据模型（Room v6）
- `local_folder(id: Long PK auto, displayName: String, rootUri: String, addedAt: Long)`
- `local_book(id: Long PK auto, folderId: Long?, documentUri: String, displayName: String, relativePath: String, format: String, sizeBytes: Long, available: Boolean, lastReadAt: Long, importedAt: Long)`
- `ReaderDatabase` 升到 6，`MIGRATION_5_6`：建两表 + `recent_reading` 增加 `source_kind` / `source_label` 列。
- `ReaderDao` 新增：
  - `upsertLocalFolder`、`deleteLocalFolder`、`getLocalFolders`
  - `upsertLocalBook`、`updateLocalBookAvailability`、`deleteLocalBook`
  - `searchLocalBooks(folderId)`、`getLocalBook(id)`
- `ReaderBackupRepository` 备份版本升 3，加入 `local` 段包含 `local_folder` / `local_book`。

D2. `LocalLibraryRepository`（新增）
- `addFolder(treeUri, displayName)`：
  - `contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`。
  - 插入 `local_folder`，遍历 `DocumentFile.fromTreeUri(...)` 子文件，按扩展名筛选 `epub/pdf/txt/mobi/azw3/fb2/rtf/doc/docx`，插入 `local_book`。
- `refreshFolder(folderId)`：重新扫描，更新 `available` / `sizeBytes`；找不到的记录 `available = false`，不主动删除。
- `removeFolder(folderId)`：删除文件夹；本地书记录保留但 `folderId = null, available = false`。
- `browseByDirectory(rootUri)`：返回 `DocumentFile` 列表，点击下钻。
- `openBookStream(localBookId)`：返回 SAF `InputStream` 给 `LocalReaderViewModel` 使用。
- 当前版本 `LocalReaderViewModel` 消费 `epub/pdf/txt`；其它格式入库但不提供“打开”按钮，文案“暂不支持本地打开”。

D3. UI 屏幕（新增）
- `LocalLibraryScreen.kt`：
  - 顶部 Tab：全部 / 按文件夹 / 按目录浏览。
  - 全部视图：所有 `available == true` 的本地书，按最近阅读排序。
  - 按文件夹视图：分组卡片，每组标题为 `displayName`，下方书籍列表；右侧 overflow 菜单：刷新 / 移除文件夹。
  - 按目录浏览：`DocumentFile` 树形结构，下钻到子目录。
  - 右上 actions：`刷新全部` / `添加文件夹`。
- `LocalFolderManageScreen.kt`：设置页入口，展示已配置文件夹与统计（数量 / 总大小）；底部“添加文件夹”。

D4. 阅读本地书
- `LocalReaderViewModel`：
  - 增加 `loadLocalBook(context, localBookId)`。
  - epub / pdf 写入 `cacheDir/local_books/local_{id}.{format}` 临时文件，由 Readium 读；`onCleared` 时删除。
  - txt 写入 `cacheDir/local_books/txt_{localBookId}_{size}_{modified}.epub` 缓存文件，由 Readium 作为 EPUB 打开；缓存元数据写入 `txt_{localBookId}.json`，不随 ViewModel 清理删除。
  - 写入最近阅读时 `sourceKind = local`、`sourceLabel = folder.displayName`；更新 `local_book.lastReadAt`。
- `LocalReaderScreen` 入口：
  - `bookId: Int? = null`
  - `localBookId: Long? = null`
  - 二者取一；都不存在则报错“无效阅读入口”。
- `NavGraph` 新增 `composable("local_reader/local/{localBookId}")`。
- `ReadiumHostFragment` 暂无需改；走 `File` 临时路径，`assetRetriever.retrieve(File(...))`。

D5. 最近阅读来源标签
- `RecentReadingEntity` 增加 `sourceKind: String = "library"`（取值 `library` / `local`）与 `sourceLabel: String = ""`（书库 `server.name` 或本地文件夹名）。
- `LocalReaderViewModel` 在写入最近阅读时填充这两字段；旧数据迁移时默认 `library` / 当前 `activeServer.name`。
- `RecentReadingViewModel.RecentReadingItem` 暴露 `sourceKind` / `sourceLabel`。
- `RecentReadingScreen` 每条记录渲染 `AssistChip`，在封面下方或进度按钮右侧：
  - `sourceKind == "local"` 显示“本地”，弱 tint。
  - `sourceKind == "library"` 显示 `sourceLabel`，主 tint。

D6. 本地书架设置入口
- `SettingsScreen`“本地书架”分组：
  - 已配置文件夹列表（`displayName` + 路径 + 书籍数）。
  - 每行 actions：刷新 / 移除。
  - 底部 `添加文件夹` 按钮，使用 `ActivityResultContracts.OpenDocumentTree`，回调 `LocalLibraryRepository.addFolder`。
- 设置页新增 `local_library` 路由入口（跳 `LocalFolderManageScreen`）。
- `MainTabsScreen` 在 `homeTabs in (local, both)` 时新增 Tab：`本地`（图标 `Icons.Default.LibraryBooks`），跳 `LocalLibraryScreen`。

### 模块 E · 默认启动路径

- `MainActivity.onCreate`：
  - 收集 `skipAuth` 与 `isLoggedIn`，首次决定是否需要绕过登录。
  - 应用全局沉浸式配置（模块 A1）。
  - 调用 `SettingsRepository.migrateLegacyMargins()`，把旧 `reader_page_margins` 拆到新两键。
  - 调用 `TalebookApp.onCreate` 初始化（已经存在）：持久化 CookieJar、Room 单例。
- `NavGraph.startDestination`：`skipAuth || isLoggedIn` → `home`，否则 `login`。

### 模块 F · 测试与回归

F1. 沉浸式
- App 冷启动 → 主页面无导航栏，状态栏可见。
- 阅读页 → 状态栏按高级设置开关显隐；左下时间显示正常。

F2. 在线阅读器移除
- 设置无“阅读器模式”选项。
- 任意入口“阅读”都进 `local_reader`。

F3. 启动 / 缓存
- 第一次进主页 → 拉服务端；第二次冷启动 → 立刻显示缓存（即使飞行模式也不白屏），后台静默拉取。
- 右上刷新 → 强制拉新；下拉刷新 → 同样行为。
- 未配置服务器 → 显示空态卡片，无网络错误。

F4. 本地书架
- SAF 选文件夹 → 列表显示；进入阅读 → 最近阅读带“本地”标签。
- 移除文件夹 → 文件夹消失，本地书记录保留但不可读。
- 按目录浏览 → 子目录可下钻。

F5. 设置
- 主页标签三种状态切换 → 立即反映在 Tab 数。
- 状态栏隐藏开关打开 → 阅读页状态栏消失，左下显示时间。
- 跳过验证 → 主页只显示本地 + 设置；补齐登录后恢复。

F6. 兼容性
- `ReaderDatabase` 5 → 6 迁移；旧备份可导入（`source_kind` 默认 `library`，`source_label` 取当前激活书库名）。
- DataStore 旧 `reader_page_margins` 拆到 `reader_page_margin_horizontal` / `reader_page_margin_vertical`。

### 模块 G · 交付物 / 风险

G1. 文件级落地清单
- `app/src/main/res/values/themes.xml`
- `app/src/main/java/com/talebook/app/MainActivity.kt`
- `ui/screens/LocalReaderScreen.kt`、`BookDetailScreen.kt`、`SettingsScreen.kt`、`MainTabsScreen.kt`、`HomeScreen.kt`、`RecentReadingScreen.kt`、`LoginScreen.kt`、`NavGraph.kt`
- 新增 `ui/screens/LocalLibraryScreen.kt`、`LocalFolderManageScreen.kt`
- 新增 `util/TxtToEpubConverter.kt`
- `data/local/ReaderEntities.kt`、`ReaderDao.kt`、`ReaderDatabase.kt`、新增 `LocalLibraryRepository.kt`
- `viewmodel/LocalReaderViewModel.kt`、`HomeViewModel.kt`、`RecentReadingViewModel.kt`
- `data/repository/SettingsRepository.kt`、`ReaderBackupRepository.kt`
- 删除 `ui/screens/ReaderScreen.kt`、`viewmodel/ReaderViewModel.kt`
- `app/build.gradle.kts`、`README.md`、`DEVELOPMENT.md`

G2. 风险点
- SAF 对 `.mobi` / `.azw3` 等格式的 MimeType 在不同 ROM 行为差异，需按扩展名兜底。
- 本次对 `epub / pdf / txt` 提供本地阅读，其余格式入库但不渲染；后续单独迭代。
- 多书库切换时旧 `bookId` 可能已无效，`RecentReadingItem` 应容错显示。
- 删除在线阅读器后部分 URL 跳转可能失效，需要确认 `bookId` 链路没有遗漏。
- `LocalLibraryRepository` 扫描大量本地书时需避免主线程阻塞，扫描过程走 `Dispatchers.IO`。

### 模块 H · 不在本版本范围

- 自动抓本地书元数据（封面 / 作者 / 简介）。
- 文件夹后台监听自动刷新。
- 大量本地书的分页 / 虚拟化。
- 本地书与书库书的搜索 / 笔记 / 书签跨书库同步。

### 模块 I · TXT 格式支持

I1. 方案：TXT → 缓存 EPUB
- txt 不直接交给 Readium 解析，而是先转换为 EPUB，再走标准 EPUB 渲染路径。
- 转换在 `LocalReaderViewModel.loadLocalBook()` 的 TXT 分支中完成。
- 缓存路径：`cacheDir/local_books/txt_{localBookId}_{size}_{modified}.epub`。
- 元数据路径：`cacheDir/local_books/txt_{localBookId}.json`。
- 缓存命中时直接复用 EPUB，不重复转换。

I2. 转换器：`util/TxtToEpubConverter.kt`
- 编码检测：UTF-8 BOM → UTF-16 BOM → UTF-8 严格检测 → GB18030 → GBK 回退。
- 读取方式：逐行流式读取，不再 `readBytes()` 整本入内存。
- 章节识别：支持 `第X章`、`第X回`、`序章`、`楔子`、`Chapter X` 等标题。
- 分片策略：章节优先；单章节过大时按目标块大小拆分。
- EPUB 打包：`mimetype` STORED、`META-INF/container.xml`、`OEBPS/content.opf`、`OEBPS/nav.xhtml`、多个 XHTML 内容页。
- 文件尺寸上限：50MB。

I3. 功能复用说明
- 转换后的 EPUB 继续使用 `EpubReadiumSession` + `EpubNavigatorFactory`，复用所有 EPUB 能力：
  - 书签、笔记、高亮
  - 阅读进度自动保存与恢复
  - 全文搜索
  - 朗读 TTS
  - 阅读设置（字号、亮度、滚动、页边距、主题背景/文字色）
- 本地 TXT 的阅读体验与 EPUB 统一，不再单独做 TXT 阅读器。

I4. 限制
- 不支持 50MB 以上 TXT。
- 缓存策略依赖源文件大小和最后修改时间；SAF 对 lastModified 支持不稳定时会回退为重新转换。
- 目录识别是规则驱动，不保证覆盖所有文学排版。

### 协作与进度追踪

- 模块 A / B 是阅读器本身，单独一个 PR。
- 模块 C / E 是启动与主页，独立一个 PR。
- 模块 D 是本地书架，单独一个 PR。
- 每完成一个模块，更新本文件底部“进度”小节，并写一条 README 更新日志。
- 任何 schema 变化（Room / DataStore / 备份版本）必须在 PR 描述中标注。

### 进度（已完成）

- [x] 模块 A · 阅读器沉浸与体验
- [x] 模块 B · 删除在线阅读器
- [x] 模块 C · 登录 / 启动 / 主页缓存
- [x] 模块 D · 本地书库
- [x] 模块 E · 默认启动路径
- [x] 模块 F · 测试与回归
- [x] 模块 G · 文档与版本号
- 扫描版 PDF/OCR 能力暂不实现；文字版 PDF 能从 Readium content 抽到文本时才复用搜索/TTS 等文本能力。

## 技术栈

- Kotlin 2.1.21
- Jetpack Compose
- Jetpack Navigation Compose
- DataStore Preferences
- Room
- Retrofit + OkHttp
- Coil
- Readium Kotlin Toolkit 3.1.2
- Readium PDFium Adapter

## 关键目录

```text
app/src/main/java/com/talebook/app/
  data/
    api/                 Retrofit、Cookie、接口定义
    local/               Room Entity、DAO、Database
    repository/          业务数据仓库、缓存、设置、备份
  reader/                Readium 封装、会话、Fragment、UI 事件
  ui/
    navigation/          App 路由
    screens/             Compose 页面
  viewmodel/             页面状态和业务编排
```

## 阅读器入口

阅读入口主要经过以下文件：

- `ui/navigation/NavGraph.kt`
  - 根据 `SettingsRepository.readerMode` 判断进入本地阅读器还是在线阅读器。
- `ui/screens/BookDetailScreen.kt`
  - 本地模式展示本地阅读进度、缓存状态、缓存大小、删除本书缓存和一个“阅读”按钮。
  - 在线模式保留“在线阅读”和“全屏阅读”。
- `ui/screens/LocalReaderScreen.kt`
  - 本地 Readium 阅读器的 Compose 外壳。
  - 控制顶栏、底栏、目录、书签、搜索、阅读设置、进度条。
- `viewmodel/LocalReaderViewModel.kt`
  - 负责加载书籍详情、解析阅读源、打开 Readium 会话、管理 UI 状态。
- `reader/ReadiumHostFragment.kt`
  - 真正承载 Readium Navigator 的 Fragment。
  - 负责监听 Readium 当前 Locator、保存进度、处理跳转、应用阅读设置。

## 本地阅读器数据流

打开一本书的大致流程：

1. `LocalReaderScreen` 调用 `LocalReaderViewModel.load(context, bookId)`。
2. `BookRepository.getBookDetail(bookId)` 获取书籍详情。
3. `ReaderCacheRepository.resolveReadableSource()` 判断打开来源。
4. 如果已有缓存，返回本地 `file:` URI。
5. 如果没有缓存，返回带登录态的远程资源地址。
6. `ReadiumEngine` 使用 Readium `AssetRetriever` 和 `PublicationOpener` 打开资源。
7. 根据 Publication 类型创建 `EpubReadiumSession` 或 `PdfReadiumSession`。
8. `ReadiumSessionStore` 保存会话，返回 `sessionId`。
9. `LocalReaderScreen` 通过 `AndroidView` 挂载 `ReadiumHostFragment`。
10. `ReadiumHostFragment` 创建 `EpubNavigatorFragment` 或 `PdfNavigatorFragment`。

## Readium 会话

相关文件：

- `reader/ReadiumSessionStore.kt`
- `reader/ReadiumEngine.kt`
- `reader/ReadiumHostFragment.kt`

`ReadiumSessionStore` 是当前的内存会话表，保存：

- `sessionId`
- `bookId`
- `Publication`
- 初始 Locator
- NavigatorFactory
- 当前阅读显示设置

注意：`Publication` 需要在会话移除时关闭。当前逻辑在宿主 Activity finishing 时移除会话。

## 阅读器 UI 事件

相关文件：`reader/ReadiumUiEvents.kt`

Compose 和 Readium Fragment 之间通过轻量事件总线通信。当前事件包括：

- `centerTaps`：Readium 中央点击，通知 Compose 显示/隐藏顶栏和底栏。
- `addBookmarks`：Compose 请求 Fragment 添加当前位置书签。
- `bookmarkAdded`：Fragment 添加完成后通知 Compose 刷新书签列表。
- `goToLocators`：根据序列化 Locator 跳转。
- `goToLinks`：根据目录 Link href 跳转。
- `goToProgress`：根据全书百分比跳转。
- `goToPage`：根据页码或 readingOrder 编号跳转。PDF 按页面顺序，EPUB 按阅读顺序近似。
- `progress`：Readium 当前总进度，供 Compose 显示进度条和百分比。
- `readerSettings`：阅读显示设置变更，Fragment 应用到 Readium Navigator。
- `annotationChanged`：笔记编辑/删除后通知 Fragment 刷新正文高亮。

TTS 使用 `ReadiumUiEvents` 事件与阅读器通信：`LocalReaderViewModel` 从 `ReadiumSessionStore` 取 `Publication.content()` 构建朗读队列，每句开始时用 `emitGoToLocator()` 跟随定位，并用 `emitTtsHighlight()` 高亮当前段落。

如果要新增 Compose 控制 Readium 的能力，优先在这里加事件，避免把 Fragment 实例直接暴露给 Compose。

## 顶栏和底栏分工

当前阅读页交互约定：

- 进入阅读器默认沉浸阅读，顶栏和底栏隐藏。
- 点击阅读区中央显示或隐藏顶栏和底栏。
- 顶部放高频操作：返回、书签、搜索、缓存本书。
- 底部放阅读控制：目录、主题、阅读设置、笔记。
- 底部常驻细进度条，右下角显示小字号百分比。
- 点击进度数字会弹出跳转框，支持百分比或页码/阅读顺序编号跳转，避免拖拽误操作。

相关代码集中在 `LocalReaderScreen.kt`：

- 顶栏：`Scaffold(topBar = ...)`
- 底栏：`ReaderBottomBar`
- 常驻进度：`ReaderProgressOverlay`
- 目录弹窗：`showTocDialog`
- 书签弹窗：`showBookmarksDialog`
- 搜索弹窗：`showSearchDialog`
- 阅读设置弹窗：`showSettingsDialog`
- TTS 面板：`ttsState.isPanelVisible`
- TTS 浮动控制栏：朗读或暂停时显示，提供上一句、暂停/继续、下一句、关闭和打开完整面板。

## 阅读设置

相关文件：

- `data/repository/SettingsRepository.kt`
- `reader/ReadiumUiEvents.kt`
- `reader/ReadiumHostFragment.kt`
- `viewmodel/LocalReaderViewModel.kt`
- `ui/screens/LocalReaderScreen.kt`

当前已支持的阅读设置：

- 字号
- 行距
- 页边距
- 滚动模式
- 夜间模式
- 亮度跟随系统
- 手动亮度
- 屏幕常亮
- 主题跟随 App 设置
- 字体、高级排版、翻页动画和音量键翻页位于高级阅读设置。

EPUB 使用 `EpubPreferences` 实时应用：

- `fontSize`
- `lineHeight`
- `pageMargins`
- `paragraphSpacing`
- `publisherStyles`
- `scroll`
- `theme`

亮度通过 Android Window `screenBrightness` 应用：

- 跟随系统：`screenBrightness = -1f`
- 手动亮度：`screenBrightness = 0.3f..1.0f`

PDF 当前支持打开、进度保存、书签、当前位置笔记、反 L 点击翻页、音量键翻页、百分比/页码跳转。PDF 搜索、PDF 选区笔记、适应宽度/页面等仍需要继续专项验证。

## TTS

相关文件：

- `reader/TtsController.kt`
- `viewmodel/LocalReaderViewModel.kt`
- `ui/screens/LocalReaderScreen.kt`
- `data/repository/SettingsRepository.kt`

当前 TTS 能力：

- 使用 Android 系统 `TextToSpeech`。
- 从 Readium `Publication.content().elements()` 提取文字。
- 按标点和长度切成句子队列。
- 默认从当前阅读进度附近开始朗读。
- 每句开始时通过 `ReadiumUiEvents.emitGoToLocator()` 跳到该句所属 Locator，实现自动跟随/翻页。
- 每句开始时通过 `ReadiumUiEvents.emitTtsHighlight()` 通知阅读器以浅蓝 `Decoration.Style.Highlight` 高亮当前段落（独立装饰组 `"talebook-tts"`，与批注组 `"talebook-annotations"` 互不冲突）。
- 支持开始、暂停、继续、停止、上一句、下一句。
- 支持语速、音调、系统语音选择。
- 支持睡眠模式，默认开启 30 分钟自动停止。
- 朗读或暂停时显示阅读页浮动控制栏；完整控制仍在 TTS 面板。
- 熄屏后不主动停止朗读。
- 音频焦点：`TtsController` 在朗读前申请 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`；临时丢失焦点自动暂停、恢复焦点自动继续（回调 `onFocusLoss`/`onFocusGain` 接入 ViewModel）。
- TTS 面板内置电池优化提示与"电池优化设置"入口（`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`），应对部分机型后台播放被杀问题。

TTS 持久化设置：

- `tts_speech_rate`
- `tts_pitch`
- `tts_voice_name`
- `tts_sleep_enabled`
- `tts_sleep_minutes`

限制：

- 不做通知栏媒体控制（播放/暂停/上一句/下一句等锁屏控件）。
- PDF 能抽到文本就朗读，抽不到文本显示“本书不支持朗读”。
- OCR 不在计划内。

## 本地数据存储

相关文件：

- `data/local/ReaderEntities.kt`
- `data/local/ReaderDao.kt`
- `data/local/ReaderDatabase.kt`

Room 表：

- `reading_progress`：阅读进度，保存 `bookId`、序列化 `Locator`、总进度。
- `reader_bookmarks`：书签，保存标题、Locator、进度。
- `reader_annotations`：标注/笔记。EPUB 选区笔记会通过 Readium Decoration 在正文高亮；无选区或 PDF 保存当前位置笔记。
- `reader_cache`：缓存元数据，保存文件路径、大小、格式、访问时间。

## 缓存策略

相关文件：`data/repository/ReaderCacheRepository.kt`

规则：

- 缓存在 App 私有目录。
- 打开阅读器时不强制缓存。
- 已缓存则打开本地文件。
- 未缓存则流式打开远程资源。
- 阅读器顶部提供“缓存本书”。
- 设置页提供缓存上限、统计占用、一键清理、Wi-Fi 自动缓存。
- 设置页提供“查看已缓存”入口，进入独立已缓存图书页面；列表内每本书支持删除缓存、进入详情页、直接阅读。
- 书籍详情页在本地阅读器模式下显示本地阅读进度、缓存状态、缓存大小和删除本书缓存。
- 缓存上限通过 LRU 方式裁剪。

格式处理：

- EPUB：直接读取。
- PDF：直接读取。
- AZW3/MOBI：通过 talebook `/get/extract/{bookId}/` 转 EPUB 后进入同一阅读管线。

## 备份导入导出

相关文件：`data/repository/ReaderBackupRepository.kt`

当前导出内容：

- 阅读进度
- 书签
- 笔记/标注

导出位置：`Download/talebook/`

文件名格式：`talebook-reader-backup-yyyyMMdd-HHmmss.json`

导入当前行为：

- 从 `Download/talebook/` 查找最近一次备份。
- 设置页点击导入前会先弹确认框。
- 进度按 Room `bookId` 保存。
- 书签按 `bookId + locatorJson` 去重。
- 笔记按 `bookId + locatorJson + selectedText` 去重。

导入预览不在当前计划内。

## 在线阅读器

相关文件：`ui/screens/ReaderScreen.kt`

在线阅读器是兼容兜底，不是 v2.0 的主要方向。当前保留 WebView 阅读，并包含已验证补丁：

- 默认隐藏网页阅读器工具栏。
- 修复滚动翻页时上下页文字重叠的问题。

后续不要继续扩展在线阅读器功能，除非是阻断性兼容问题。

## 常见修改入口

新增顶部快捷按钮：

- 修改 `LocalReaderScreen.kt` 的 `TopAppBar(actions = ...)`。
- 如果需要控制 Readium，给 `ReadiumUiEvents.kt` 加事件，并在 `ReadiumHostFragment.kt` 处理。

新增底部阅读工具：

- 修改 `LocalReaderScreen.kt` 的 `ReaderBottomBar`。
- 弹窗状态一般放在 `LocalReaderScreen` 顶部 `remember` 状态里。

新增持久化阅读设置：

- 在 `SettingsRepository.kt` 增加 DataStore key 和 Flow。
- 扩展 `ReaderDisplaySettings`。
- 在 `LocalReaderViewModel.load()` 读取初始值。
- 在 `LocalReaderViewModel.updateReaderSettings()` 保存。
- 在 `ReadiumHostFragment.applyReaderSettings()` 应用到 Navigator 或 Android Window。

新增 Readium 跳转能力：

- Locator 跳转用 `ReadiumUiEvents.emitGoToLocator()`。
- 目录 Link 跳转用 `ReadiumUiEvents.emitGoToLink()`。
- Fragment 内通过 `navigator.go(locator)` 或 `HyperlinkNavigator.go(link)` 执行。

新增本地数据库字段：

- 修改 `ReaderEntities.kt`。
- 修改 `ReaderDao.kt`。
- 更新 `ReaderDatabase` 的版本和迁移策略。
- 注意已有用户数据时不能直接破坏表结构。

## 当前待开发重点

优先级从高到低：

1. 搜索结果显示优化：列表摘要、空状态、搜索中状态、分页/加载更多。
2. 软件本身的主题颜色设置：在现有白天/夜间/自动基础上增加主题色选择。
3. 笔记颜色设置：保存颜色并应用到 EPUB Decoration。
4. 真实翻页动画效果：滑动、覆盖等动画需要后续验证或自研实现；不要在 UI 暴露当前无法区分的假选项。
5. PDF 文字版深化：PDF 搜索、PDF 选区笔记、PDF 适应宽度/适应页面能力验证。
6. TTS 页内体验优化：浮动控制栏细节、当前朗读句显示、系统语音兼容性。
7. Readium 会话生命周期和大书性能优化。

明确不做：

- TTS 通知栏媒体控制。
- 缓存进度显示。
- 缓存失败原因细化。
- 导入前预览。
- OCR/扫描版 PDF 文本识别。
- 搜索正文高亮。
- 最近阅读列表、阅读统计、点击区域示意图、工具栏自动隐藏、屏幕方向锁定、真实翻页动画深化。

## 构建和安装

Debug 构建：

```powershell
.\gradlew assembleDebug
```

安装到已连接设备：

```powershell
adb install -r "F:\aicode\talebook-android\app\build\outputs\apk\debug\app-debug.apk"
```

复制测试包到桌面：

```powershell
Copy-Item "F:\aicode\talebook-android\app\build\outputs\apk\debug\app-debug.apk" "$env:USERPROFILE\Desktop\talebook-2.2.0-debug.apk" -Force
```

## 开发注意事项

- 优先做小而明确的改动，不要一次性重写阅读器架构。
- Readium API 中部分接口标记为 experimental，升级 Readium 前要单独验证。
- Compose 层不要直接保存 Fragment 引用。
- Fragment 层不要直接依赖 Compose 状态，跨层通信走 `ReadiumUiEvents`。
- 如果涉及 PDF，必须单独测试 PDF，不要只用 EPUB 验证。
- 如果涉及 Room schema，必须考虑迁移和已有数据。
