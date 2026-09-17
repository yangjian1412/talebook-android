# Talebook Android

> 当前版本：`2.2.3beta3`
>
> Talebook Android 是 [talebook](https://github.com/talebook/talebook) 服务端的第三方 Android 阅读客户端。项目定位是：**App 负责阅读体验，talebook 服务器只负责存书、登录和提供书籍资源。**

[![GitHub Repo](https://img.shields.io/badge/GitHub-仓库-blue?logo=github)](https://github.com/yangjian1412/talebook-android)

### 2.2.3beta3 更新日志

- 极验（GeeTest v4）原生支持：**待测试**。服务端启用极验 captcha 时，App 在登录页和设置页书库配置自动探测 `api/captcha/config`，使用内置 WebView 加载 `gt4.js` SDK 完成拼图/滑动验证，验证成功后自动提交 4 个参数（`lot_number`、`captcha_output`、`pass_token`、`gen_time`）到 `api/welcome` / `api/user/sign_in`，由服务端调极验 `/validate` 完成最终校验。
- 新的 WebView 组件：`app/src/main/java/com/talebook/app/ui/components/GeetestCaptchaView.kt`，通过 `addJavascriptInterface("AndroidBridge")` 把极验结果回传到 Kotlin。HTML 容器位于 `app/src/main/assets/geetest.html`，按需 `DisposableEffect` 中 `destroy()` 释放内存。
- API 扩展：`TalebookApi.loginWithCode` / `loginWithPassword` 新增 4 个可选字段（极验），`AuthRepository` 新增 `GeetestParams` 数据类；`unlockSite` / `loginWithPassword` 增加可选 `geetest` 参数。
- 极验与 image captcha 共存：`CaptchaStatus.Image` 显示 PNG + 输入框，`CaptchaStatus.Geetest` 显示 WebView，验证完成后自动 POST 请求，不需要再点确认按钮。
- 与现有登录模式完全兼容：不启用 captcha、image captcha、极验三种模式按服务端配置自动选择，cookie 按 host 隔离互不影响；多书库切换与会话持久化逻辑不变。
- **已知风险**：极验 SDK 依赖境外 CDN（`static.geetest.com`、`gcaptcha4.geetest.com`），国内网络下可能超时；部分国产 ROM 替换 WebView 实现可能报"环境不安全"，需要服务端关闭极验或用户切换为浏览器截图。
- **测试状态**：当前 APK 已在本地机器启动测试，**尚未在真实极验服务端验证端到端流程**；欢迎有极验配置的用户反馈测试结果。
- 应用版本升级至 2.2.3beta3。

### 2.2.3beta2 更新日志

- 完整人机验证支持：客户端支持服务端图形验证码（image provider）。服务端启用 captcha 时，登录页与设置页书库配置会自动探测 `api/captcha/config`，需要时显示验证码图片 + 输入框 + 刷新按钮；登录前必须输入正确的验证码，验证码 cookie 由 OkHttp 持久化（2 分钟有效）。
- 极验（GeeTest v4）暂不内置支持：服务端启用极验时弹说明提示，引导用户先在 Web 端完成首次登录以建立 cookie。
- 设置页多书库登录体验统一：每次新增/编辑书库都走完整登录流程——探测 unlockSite → 探测 login captcha → 必要时依次弹访问码对话框和登录验证码对话框。完成登录后自动写入 cookie（OkHttp PersistentCookieJar）。
- 抽象 `UnlockSiteDialog` / `LoginCaptchaDialog` 共用组件（`app/src/main/java/com/talebook/app/ui/components/CaptchaDialogs.kt`），登录页与设置页复用同一套实现。
- 应用版本升级至 2.2.3beta2。

### 2.2.3beta1 更新日志

- 修复 Talebook v26.9.1+ 站点访问码登录失败：客户端 `api/welcome` 字段名由 `code` 修正为服务端要求的 `invite_code`。
- 支持 Talebook 私人模式（INVITE_MODE）：服务器配置对话框新增"是否启用私人模式"开关，启用后增加"私人模式访问码"输入项；登录流程会先调用 `api/welcome` 解锁站点，再走账号密码/访问码/访客登录。
- 老用户数据兼容：`LibraryServerConfig` 新增字段为 nullable，老 JSON 数据反序列化不再崩溃。
- 应用版本升级至 2.2.3beta1。

### 2.2.3beta1 补充（人机验证支持）

- 支持服务端图形验证码（image provider，Talebook 默认无配置 captcha）：登录页密码表单底部自动探测 `api/captcha/config`，启用时展示验证码图片 + 输入框 + 刷新按钮。`api/welcome`（解锁站点）与 `api/user/sign_in`（账号登录）都会在启用 captcha 的场景下要求携带 `captcha_code` 字段。验证码 cookie（`captcha_answer` / `captcha_generate_time`）由 OkHttp `PersistentCookieJar` 持久化，2 分钟有效。
- 极验（GeeTest v4）暂不内置支持：服务端启用极验时，App 登录页会弹出说明弹窗，引导用户先在浏览器登录一次以建立 cookie，后续会话由 cookie 维持，避免空提示。
- 失败自动刷新：登录或解锁返回 `captcha.invalid` 时自动刷新一张新图，无需手动刷新。
- 兼容未启用 captcha 的服务端：探测到 `config.enabled = false` 或 `scenes.login/welcome = false` 时不显示验证码 UI，行为与之前一致。

### 2.2.2beta2 更新日志

- 移除应用内置的默认服务器地址：首次启动必须手动配置服务器 URL，不再自动填充任何默认地址。
- 阅读时显示当前书名与章节标题：在底部进度条下方居中显示"书名 > 章节名"，各最多 12 字后省略号截断。
- 高级设置拆分为独立开关：阅读时隐藏状态栏 / 隐藏时间 / 隐藏书名和章节 三个独立开关，默认都显示。
- 字号范围 50%~300%、行距 0.5~3、页边距 0.5~3、亮度 0~100%、字间距 0~10、段间距 0~4。
- 应用版本升级至 2.2.2beta2。

### 2.2.2beta 更新日志

- 朗读段落高亮：正在朗读的句子所在段落以浅蓝高亮显示，跟随朗读自动翻页。
- 音频焦点处理：朗读时来电或其他应用播放音频会自动暂停，结束后自动恢复。
- 朗读面板新增电池优化提示与"电池优化设置"按钮，解决部分机型后台无法播放的问题。
- 应用版本升级至 2.2.2beta。

### 2.2.2alpha 更新日志

- 朗读锁屏播放：开启朗读后锁屏可继续播放（后台 Foreground Service + WakeLock 实现，无通知栏控件）。
- 朗读设置面板：改为从顶部滑入的浮动面板，与迷你播放条上下共存。
- 朗读控制图标化：迷你播放条与设置面板的"播放/暂停/上一句/下一句/关闭"按钮改用标准 Material 图标。
- 状态栏适配：迷你播放条与设置面板自动避让状态栏与刘海屏。
- 应用版本升级至 2.2.2alpha。

## 功能特性

### 阅读入口

- 默认打开 App 后，登录状态下显示书库主页；可选择跳过验证直接进入（仅可使用本地书架与设置）。
- 设置中可调整主页标签的可见性：只显示书库 / 只显示本地 / 同时显示。

### 当前已实现

- 本地阅读器基于 Readium Kotlin Toolkit 3.1.2。
- 支持 EPUB/PDF 的 Readium Navigator 挂载。
- 无缓存时支持带登录 Cookie 的远程流式阅读。
- 有缓存时优先读取 App 私有缓存。
- 支持手动缓存本书。
- 支持 Wi-Fi 下自动缓存开关。
- 支持缓存容量上限设置。
- 支持一键清理阅读缓存。
- 支持统计当前阅读缓存占用。
- 支持本地阅读进度自动保存到 Room。
- 支持打开书籍时恢复上次 Locator 位置。
- 全局沉浸：App 启动即隐藏导航栏，状态栏默认保留；阅读时可在高级设置里再隐藏状态栏，左下进度位置显示当前时间。
- 阅读器工具栏改为覆盖式悬浮，显隐不修改 WebView padding / 高度，避免重新排版。
- 支持添加书签。
- 支持书签列表、跳转、删除。
- 书签默认标题优先使用当前位置附近的文本摘要。
- 支持目录面板和目录跳转。
- 支持笔记：选区笔记、当前位置笔记、正文高亮、列表跳转、编辑、删除。
- 支持全文搜索基础能力：搜索文本元素、显示结果、点击跳转。
- 支持阅读设置：字号、行距、左右 / 上下独立页边距、亮度、滚动模式、常亮、主题背景与文字颜色。
- 支持高级阅读设置：字体、翻页动画、音量键翻页、点击翻页、滚动模式点击翻页、段间距、出版社样式、强制使用出版社字体、阅读时隐藏状态栏。
- 支持点击底部进度数字打开跳转框，按百分比或页码/阅读顺序编号跳转。
- 支持导出阅读记录、书签、笔记到 `Download/talebook/`。
- 支持从 `Download/talebook/` 导入最近一次阅读数据备份，并对书签/笔记去重。
- PDF 支持 Readium PDFium 打开、进度保存、书签、当前位置笔记、反 L 点击翻页、音量键翻页、百分比/页码跳转。
- 主页首屏显示缓存（右上角 Refresh + 下拉刷新），未配置服务器显示空态卡片。
- 本地书架：SAF 选择文件夹作为书库，不复制源文件，支持 epub / pdf / txt 阅读；最近阅读页面对本地书与书库书分别显示来源标签。
- TXT 阅读会自动转换为缓存 EPUB，支持编码识别、章节目录、缓存复用；后续打开同一 TXT 不重复转换。
- 设置中提供 "跳过验证直接进入"、"主页标签"、"本地书架" 等开关。

### 2.2.1b 更新日志

- 修复部分 EPUB 书籍无法设置字体/字号的问题。
- 改进"强制使用出版社字体"开关逻辑：关闭时通过 CSS `!important` 强制覆盖出版社的内联样式（包括字号和字体），使所有书籍的字体字号可正常调整。

### 2.2.0 更新日志

- 移除在线阅读器，所有阅读入口统一走 Readium 本地阅读器。
- 新增本地书架：通过 SAF 添加本地文件夹，不复制源文件，支持 epub / pdf / txt。
- TXT 本地阅读升级：流式转换为 EPUB、自动识别章节目录、缓存转换结果、支持 UTF-8 / UTF-16 / GB18030 / GBK。
- 优化书库打开体验：主页缓存、下拉刷新、未配置服务器空态。
- 软件不再强制登录，可跳过验证直接进入本地书架与设置。
- 全局隐藏导航栏；阅读器默认保留状态栏，可在高级设置中强制隐藏。
- 阅读器固定为沉浸式覆盖工具栏，不再提供非全屏阅读方式。
- 可分开配置上下和左右页边距。
- 最近阅读新增书源标记，可区分本地书和书库书。
- 增加开屏画面，已登录状态不再显示登录页面。
- 重做阅读器主题和背景系统，EPUB 书页层通过 Readium preferences 应用背景色与文字色。
- 修复本地书进度、书签、笔记按本地书 ID 保存和读取的问题。


### 2.1.0 更新日志

- 完善了缓存管理，增加缓存下载进度管理。
- 增加了字体和背景颜色设置及预设。
- 增加了笔记按书导出 md 格式。
- 修复了部分书无法阅读的 bug。
- 软件本身的主题颜色设置。
- 重构显示方式，设计为三标签显示方式并自定义配置首页。
- 增加最近阅读首页，增加编辑功能、置顶/移顶、收起分组。
- 增加多书库支持。
- 修复了部分书无法阅读的 bug。
- 修复远程阅读大字体 EPUB 在 WebView 下出现白屏的问题，新增"强制使用出版社字体"开关。

### 待开发计划

- 搜索结果显示优化：列表摘要、空状态、搜索中状态和分页/加载更多。
- 笔记颜色设置。
- 真实翻页动画效果：滑动、覆盖等动画需要后续验证或自研实现。
- PDF 专项深化：文字版 PDF 搜索、文字版 PDF 选区笔记、PDF 适应宽度/适应页面能力验证；需要 OCR 的扫描版 PDF 能力暂不实现。
- TTS 后续优化方向：通知栏媒体控制（锁屏播放控件）、TTS 语速/音调实时预览、多语言语音支持。
- 字体选择功能。
- 多语言版本

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

## 格式兼容

| 格式 | 当前处理方式 |
|---|---|
| EPUB | Readium 直接渲染 |
| PDF | Readium PDFium Navigator |
| TXT | 自动转换为缓存 EPUB 后走 Readium 渲染；支持章节目录、编码识别和缓存复用，书签/笔记/TTS/设置完全复用 |
| AZW3 / MOBI | 通过 talebook `/get/extract/{bookId}/` 转为 EPUB 后阅读，可缓存转换结果 |

## 本地数据

本地阅读数据使用 Room 存储：

- `reading_progress`：阅读进度 Locator。
- `reader_bookmarks`：书签。
- `reader_annotations`：标注/笔记。
- `reader_cache`：阅读缓存元数据。

书籍文件缓存保存在 App 私有目录，不依赖公开下载目录。公开下载目录仅用于用户主动导出数据或下载书籍。

## 构建说明

项目当前配置：

| 项目 | 版本 |
|---|---|
| minSdk | 26 |
| targetSdk | 36 |
| compileSdk | 36 |
| AGP | 8.7.3 |
| Gradle Wrapper | 8.14.3 |
| Kotlin | 2.1.21 |

构建 Debug APK：

```bash
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew assembleDebug
```

## 项目状态

`2.2.1b` 已完成。修复部分书籍无法设置字体/字号的 bug。

开发者请参考 `DEVELOPMENT.md`，其中包含源码结构、阅读器事件流、数据存储和常见修改入口。

## 设计原则

- App 是阅读入口，talebook 是存书服务器。
- 本地阅读器优先，不依赖网页阅读器实现核心阅读体验。
- 本地书架与书库可以独立使用：跳过验证即可只读本地书；补齐登录后可同时读本地与书库。
- 书签、笔记、进度优先本地可靠保存。
- 暂不做账号级多端同步，可通过导入/导出备份实现迁移。
