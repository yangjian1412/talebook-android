@file:OptIn(androidx.window.core.ExperimentalWindowApi::class)

package com.talebook.app.reader

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionPresenter
import coil.Coil
import coil.request.ImageRequest
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.util.resolveUrl
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "TaleCoverScreen"

/**
 * 折叠屏外屏封面。
 *
 * 首选官方 androidx.window.area（窗口区域）能力：折叠屏展开时系统会提供 TYPE_REAR_FACING
 * 的窗口区域，通过 presentContentOnWindowArea 把封面大图显示到那块屏上，给自己看的是内屏正文，
 * 给别人看的是外屏封面。
 *
 * 设备没有该区域、或书籍没有封面时不做任何事；折叠状态变化导致区域不可用时自动结束会话。
 *
 * 直连外屏（实验路径）：部分厂商不提供背屏区域，但系统里仍注册着内置外屏，
 * 这时直接用 Presentation 往那块屏画封面。这类机型展开态默认只给内屏供电，
 * 所以这里会自己尝试点亮外屏一次：先试系统的点亮屏幕接口，再试 cmd display enable-display，
 * 两者都失败时把原因写进日志。
 * 自己点亮的那块屏由自己负责关电：关闭开关、退出阅读器、退出应用时会给它下电
 * （先试 cmd display power-off，再试 power-reset）；系统或别的途径点亮的一律不动，
 * 发生折叠后那块屏会变成当前屏幕，这时也不下电。
 * 另外监听屏幕状态变化：外屏被系统关掉电（熄屏、省电）之后，封面会自动重新点亮，
 * 不需要重新打开开关；应用被强制停止而来不及下电时，下次启动会补做（见 CoverScreenPower）。
 * 另有备用触发方式：把全局设置 talebook_cover_debug 置为 1（供 adb 调试用）。
 */
class CoverScreenController(private val activity: Activity) {

    private val controller = WindowAreaController.getOrCreate()
    private val directExecutor = Executor { command -> command.run() }
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private var rearArea: WindowAreaInfo? = null
    private var wantedCoverPath: String? = null
    private var allowDirectDisplay = false
    private var windowAreaSession: WindowAreaSession? = null
    private var presentation: CoverPresentation? = null
    private var coverView: ImageView? = null
    private var requestInFlight = false
    private var waitDisplayJob: Job? = null
    private var loggedWaitHint = false
    private var loggedInventory = false
    private var loggedWindowAreas = false
    private var lastTargetState = Int.MIN_VALUE
    private var lastLoadedUrl: String? = null
    private var loadedForView: ImageView? = null
    private var triedPowerOn = false
    private var outerScreenAvailable = false

    /**
     * 由本应用点亮的外屏 id。只有这块屏才由我们负责下电，
     * 系统自己点亮、或用 adb 点亮的一律不动。
     */
    private var poweredOnDisplayId: Int? = null

    /**
     * 外屏因为折叠而不再可用时通知调用方（界面用它把开关关掉）。
     * 只在「本来有、后来没有」这种变化时触发，不是每次判断失败都触发。
     */
    var onOuterScreenLost: (() -> Unit)? = null

    private var displayChangesObserved = false

    /** 屏幕电源状态变化（系统熄屏、省电关电、我们点屏/下电）都会触发这里。 */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (wantedCoverPath == null) return
            if (displayId == currentDisplayId()) return
            Log.d(TAG, "屏幕 id=$displayId 状态变化，重新确认外屏封面")
            syncSession()
        }
    }

    /**
     * 持续跟踪系统提供的窗口区域。只有折叠屏展开、外屏可用时才有背屏区域，
     * 因此这个监听本身就实现了「只对折叠屏展开生效」。
     */
    suspend fun observeWindowAreas() {
        controller.windowAreaInfos.collect { infos ->
            rearArea = infos.firstOrNull { area -> area.canPresentCover() }
            if (!loggedWindowAreas) {
                loggedWindowAreas = true
                if (infos.isEmpty()) {
                    Log.d(TAG, "系统的窗口区域为空：这台设备没有提供官方背屏能力")
                } else {
                    Log.d(TAG, "系统的窗口区域：${infos.map { area -> "type=${area.type} 能力=${capabilityStatusOf(area)}" }}")
                }
            }
            syncSession()
        }
    }

    /**
     * 监听屏幕状态变化：外屏被系统熄掉或关电之后重新确认封面，
     * 需要时会顺手把外屏点亮，不会出现「窗口还在但屏没电」。
     */
    fun observeDisplayChanges() {
        if (displayChangesObserved) return
        val displayManager = activity.getSystemService(DisplayManager::class.java) ?: return
        runCatching { displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper())) }
            .onSuccess { displayChangesObserved = true }
            .onFailure { error -> Log.w(TAG, "监听屏幕状态变化失败：$error") }
    }

    private fun capabilityStatusOf(area: WindowAreaInfo): Any? = runCatching {
        (area.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA) as WindowAreaCapability?)?.status
    }.getOrNull()

    /**
     * 需要在外屏显示的封面（服务器返回的相对或绝对路径）；空字符串表示不需要显示。
     *
     * @param allowDirect 系统没有背屏区域时，是否允许直接用内置外屏显示（界面上的开关）。
     */
    fun showCover(coverPath: String, allowDirect: Boolean = false) {
        wantedCoverPath = coverPath.takeIf { it.isNotBlank() }
        allowDirectDisplay = allowDirect
        syncSession()
    }

    fun hideCover() {
        wantedCoverPath = null
        allowDirectDisplay = false
        stopShowing()
    }

    /** 离开阅读器时调用，释放外屏。 */
    fun release() {
        wantedCoverPath = null
        allowDirectDisplay = false
        rearArea = null
        outerScreenAvailable = false
        onOuterScreenLost = null
        loggedInventory = false
        loggedWindowAreas = false
        lastLoadedUrl = null
        stopObservingDisplayChanges()
        stopShowing()
    }

    private fun stopObservingDisplayChanges() {
        if (!displayChangesObserved) return
        displayChangesObserved = false
        val displayManager = activity.getSystemService(DisplayManager::class.java) ?: return
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
            .onFailure { error -> Log.w(TAG, "取消监听屏幕变化失败：$error") }
    }

    private fun WindowAreaInfo.canPresentCover(): Boolean {
        if (type != WindowAreaInfo.Type.TYPE_REAR_FACING) return false
        val capability = getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA) as WindowAreaCapability?
        return when (capability?.status) {
            // ACTIVE 表示正被本会话使用，此时仍需保留区域，否则会被自己关掉形成循环。
            WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE,
            WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE -> true

            else -> false
        }
    }

    private fun syncSession() {
        val coverPath = wantedCoverPath
        if (coverPath == null) {
            stopShowing()
            return
        }
        val area = rearArea
        if (area != null) {
            stopDirectDisplay()
            presentOnWindowArea(area, coverPath)
            return
        }
        if (!isDirectDisplayAllowed()) {
            if (!loggedWaitHint) {
                loggedWaitHint = true
                Log.d(TAG, "跳过：系统没有提供背屏区域，也没有允许直连外屏")
            }
            stopShowing()
            return
        }
        presentOnSecondaryDisplay(coverPath)
    }

    private fun presentOnWindowArea(area: WindowAreaInfo, coverPath: String) {
        if (windowAreaSession != null) {
            loadCover(coverPath)
            return
        }
        if (requestInFlight) return
        requestInFlight = true
        runCatching {
            controller.presentContentOnWindowArea(area.token, activity, directExecutor, object : WindowAreaPresentationSessionCallback {
                override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                    requestInFlight = false
                    val current = wantedCoverPath
                    if (current == null) {
                        runCatching { session.close() }
                        return
                    }
                    val view = coverImageView(session.context)
                    runCatching { session.setContentView(view) }
                        .onFailure { error -> Log.w(TAG, "外屏设置封面视图失败: $error") }
                    windowAreaSession = session
                    coverView = view
                    loadCover(current)
                    Log.d(TAG, "已通过背屏区域显示封面")
                }

                override fun onSessionEnded(t: Throwable?) {
                    requestInFlight = false
                    windowAreaSession = null
                    coverView = null
                }

                override fun onContainerVisibilityChanged(isVisible: Boolean) = Unit
            })
        }.onFailure { error ->
            requestInFlight = false
            Log.w(TAG, "外屏显示封面失败: $error")
        }
    }

    /** 调试路径：直接把封面窗口建到内置外屏上。 */
    private fun presentOnSecondaryDisplay(coverPath: String) {
        logDisplayInventory()
        val display = findSecondaryDisplay()
        if (display == null) {
            // 合上手机时外屏会变成当前屏幕，这时必须收起封面窗口，否则会盖在阅读界面上。
            dismissPresentation()
            if (outerScreenAvailable) {
                outerScreenAvailable = false
                Log.d(TAG, "外屏已不可用（发生折叠），通知界面关闭开关")
                onOuterScreenLost?.invoke()
            }
            if (!loggedWaitHint) {
                loggedWaitHint = true
                Log.w(TAG, "没有可用作外屏的显示屏（见上面的屏幕清单）")
            }
            waitForSecondaryDisplay()
            return
        }
        outerScreenAvailable = true
        noteTargetPowerState(display)
        val existing = presentation
        if (existing != null) {
            if (existing.display?.displayId == display.displayId) {
                loadCover(coverPath)
                waitForSecondaryDisplay()
                return
            }
            dismissPresentation()
        }
        runCatching {
            val cover = CoverPresentation(activity, display)
            cover.show()
            presentation = cover
            coverView = cover.coverImageView
            loadCover(coverPath)
            Log.d(TAG, "已在 id=${display.displayId} 的外屏建立封面窗口（调试路径）")
            // 外屏没电时先建好窗口，再观察它什么时候被点亮。
            waitForSecondaryDisplay()
        }.onFailure { error ->
            Log.w(TAG, "直连外屏显示失败: $error")
        }
    }

    /**
     * 选择要显示封面的外屏。
     *
     * 判断顺序是有意的：实测这台机器在展开态会把 FLAG_PRESENTATION 标记挂到内屏上，
     * 所以标记只能当参考；真正的依据是「不是当前屏幕，且面积比当前屏幕小」——
     * 折叠屏的外屏一定比内屏小，这一条同时也保证了只在展开态才会走到这里。
     * 也不要求外屏已点亮：窗口先建好，等系统给电时封面已经在那里了。
     */
    private fun findSecondaryDisplay(): Display? {
        val displayManager = activity.getSystemService(DisplayManager::class.java) ?: return null
        val currentId = currentDisplayId()
        val currentArea = currentId?.let { displayManager.getDisplay(it) }?.area() ?: 0L
        val candidates = SECONDARY_DISPLAY_ID_RANGE
            .mapNotNull { id -> displayManager.getDisplay(id) }
            .filter { display -> display.displayId != currentId }
            .filter { display -> currentArea == 0L || display.area() in 1 until currentArea }
            .sortedByDescending { display -> if (CoverScreenPower.isLit(display)) 1 else 0 }
        return candidates.firstOrNull { display -> display.flags and Display.FLAG_PRESENTATION != 0 }
            ?: candidates.firstOrNull()
    }

    private fun currentDisplayId(): Int? = runCatching { activity.display?.displayId }.getOrNull()

    @Suppress("DEPRECATION")
    private fun Display.area(): Long = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mode = getMode()
            mode.physicalWidth.toLong() * mode.physicalHeight
        } else {
            width.toLong() * height
        }
    }.getOrDefault(0L)

    /** 外屏电源状态变化时只记一次，方便只靠日志判断卡在哪一步。 */
    private fun noteTargetPowerState(display: Display) {
        val state = CoverScreenPower.stateOf(display)
        if (state == lastTargetState) return
        lastTargetState = state
        if (state == Display.STATE_ON) {
            Log.d(TAG, "外屏 id=${display.displayId} 已点亮（state=ON），封面应当已经显示")
        } else {
            Log.w(
                TAG,
                "外屏 id=${display.displayId} 未点亮（state=$state），画上去也不会显示。" +
                    "实测这类机型展开态默认只给内屏供电，可试 adb shell cmd display enable-display ${display.displayId}"
            )
            tryPowerOnSecondaryDisplay(display)
        }
    }

    /** 把系统视角下的屏幕列表打进日志，便于只看日志定位问题。 */
    @Suppress("DEPRECATION")
    private fun logDisplayInventory() {
        if (loggedInventory) return
        loggedInventory = true
        val displayManager = activity.getSystemService(DisplayManager::class.java) ?: return
        val visible = SECONDARY_DISPLAY_ID_RANGE.mapNotNull { id -> displayManager.getDisplay(id) }
        val presentationCategory = runCatching {
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).map { display -> display.displayId }
        }.getOrDefault(emptyList())
        Log.d(
            TAG,
            buildString {
                append("屏幕清单：当前屏幕=").append(currentDisplayId())
                append(" 可直连的副屏=").append(presentationCategory)
                append(" 官方背屏区域=")
                append(if (rearArea != null) "有" else if (loggedWindowAreas) "无" else "未收到系统事件")
                appendLine()
                for (display in visible) {
                    append("  id=").append(display.displayId)
                    append(" 尺寸=").append(display.width).append('x').append(display.height)
                    append(" state=").append(CoverScreenPower.stateOf(display))
                    append(" flags=").append(Integer.toHexString(display.flags))
                    append(" 标记为副屏=").append(display.flags and Display.FLAG_PRESENTATION != 0)
                    appendLine()
                }
            }
        )
    }

    /**
     * 打开开关时自己尝试点亮外屏一次：先试系统的点亮屏幕接口，
     * 不行就直接执行一次 cmd display enable-display。两者都需要系统权限，
     * 失败会把原因写进日志。
     */
    private fun tryPowerOnSecondaryDisplay(target: Display) {
        if (triedPowerOn) return
        triedPowerOn = true
        if (tryEnableDisplayApi(target)) return
        tryEnableDisplayCommand(target)
    }

    private fun tryEnableDisplayApi(target: Display): Boolean {
        val displayManager = activity.getSystemService(DisplayManager::class.java) ?: return false
        val method = displayManager.javaClass.methods.firstOrNull { candidate ->
            candidate.name == "enableDisplay" &&
                candidate.parameterTypes.isNotEmpty() &&
                candidate.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (method == null) {
            Log.w(TAG, "系统没有向应用开放点亮屏幕的接口（没找到 enableDisplay）")
            return false
        }
        val outcome = runCatching {
            // 不同系统的 enableDisplay 参数个数不同，按签名补默认值。
            val args = arrayOfNulls<Any>(method.parameterTypes.size)
            for (index in method.parameterTypes.indices) {
                args[index] = when (method.parameterTypes[index]) {
                    Boolean::class.javaPrimitiveType -> false
                    Long::class.javaPrimitiveType -> 0L
                    else -> target.displayId
                }
            }
            method.invoke(displayManager, *args) == true
        }
        if (outcome.getOrDefault(false)) {
            markDisplayPoweredOn(target.displayId, "系统接口 ${method.name}")
            return true
        }
        Log.w(TAG, "系统的点亮屏幕接口调用失败：${outcome.exceptionOrNull()}")
        return false
    }

    /** 兜底：自己执行一次 cmd display enable-display，成功就不需要外部命令了。 */
    private fun tryEnableDisplayCommand(target: Display) {
        CoverScreenPower.runDisplayCommand("enable-display", target.displayId) { failure ->
            if (failure == null) {
                markDisplayPoweredOn(target.displayId, "命令 cmd display enable-display")
                // 点亮成功后立刻再走一次，不用等下一轮轮询。
                wantedCoverPath?.let { path -> presentOnSecondaryDisplay(path) }
            } else {
                Log.w(TAG, "应用自己点亮外屏失败：$failure")
            }
        }
    }

    /** 点亮成功：记下这块屏归我们管（只有我们能下电），日志里写明走的是哪条路，便于换机型对比。 */
    private fun markDisplayPoweredOn(displayId: Int, path: String) {
        poweredOnDisplayId = displayId
        CoverScreenPower.markPoweredUp(activity, displayId)
        Log.d(TAG, "已点亮外屏 id=$displayId（$path）")
    }

    /**
     * 给我们自己点亮过的外屏下电：关闭功能、退出阅读器、退出应用都会走到这里。
     * 系统自己点亮或用 adb 点亮的屏幕不归我们管；发生折叠后那块屏就是当前屏幕，也不能下电。
     */
    private fun powerOffDisplayWePoweredUp() {
        val displayId = poweredOnDisplayId ?: return
        poweredOnDisplayId = null
        if (displayId == currentDisplayId()) {
            Log.d(TAG, "不下电：id=$displayId 现在是当前屏幕（发生折叠）")
            CoverScreenPower.clearPoweredUp(activity)
            return
        }
        val display = CoverScreenPower.displayOf(activity, displayId)
        if (display == null) {
            Log.d(TAG, "不下电：id=$displayId 的屏幕已经不在了")
            CoverScreenPower.clearPoweredUp(activity)
            return
        }
        if (CoverScreenPower.isOff(display)) {
            Log.d(TAG, "不下电：外屏 id=$displayId 已经没有电（state=${CoverScreenPower.stateOf(display)}）")
            CoverScreenPower.clearPoweredUp(activity)
            return
        }
        CoverScreenPower.powerOff(activity, displayId)
    }

    /** 等待副屏被点亮：轮询一小段时间，期间 adb 点亮后就能立刻显示。 */
    private fun waitForSecondaryDisplay() {
        if (waitDisplayJob?.isActive == true) return
        waitDisplayJob = scope.launch {
            var attempts = 0
            while (attempts < WAIT_RETRY_TIMES) {
                delay(WAIT_RETRY_INTERVAL_MS)
                val coverPath = wantedCoverPath ?: return@launch
                if (rearArea != null) return@launch
                presentOnSecondaryDisplay(coverPath)
                attempts += 1
            }
        }
    }

    private fun stopShowing() {
        stopWindowAreaSession()
        stopDirectDisplay()
    }

    private fun stopWindowAreaSession() {
        requestInFlight = false
        val current = windowAreaSession
        windowAreaSession = null
        coverView = null
        if (current != null) {
            runCatching { current.close() }
        }
    }

    private fun stopDirectDisplay() {
        waitDisplayJob?.cancel()
        waitDisplayJob = null
        loggedWaitHint = false
        triedPowerOn = false
        lastTargetState = Int.MIN_VALUE
        dismissPresentation()
        // 归我们管的屏幕由我们关电：关闭功能、退出阅读器、退出应用都走这里。
        powerOffDisplayWePoweredUp()
    }

    /** 只收起外屏上的封面窗口，不动等待轮询。 */
    private fun dismissPresentation() {
        val current = presentation
        presentation = null
        if (current != null) {
            coverView = null
            lastLoadedUrl = null
            loadedForView = null
            runCatching { current.dismiss() }
        }
    }

    private fun coverImageView(context: Context): ImageView = ImageView(context).apply {
        setBackgroundColor(Color.BLACK)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun loadCover(coverPath: String) {
        val view = coverView ?: return
        val url = resolveUrl(coverPath)
        if (url.isBlank()) return
        // 等待外屏点亮的轮询里会反复走到这里，同一张封面不必重复请求。
        if (loadedForView === view && url == lastLoadedUrl) return
        loadedForView = view
        lastLoadedUrl = url
        val cookie = RetrofitClient.cookieHeader()
        val request = ImageRequest.Builder(view.context)
            .data(url)
            .apply { if (cookie.isNotBlank()) addHeader("Cookie", cookie) }
            .target(view)
            .build()
        runCatching { Coil.imageLoader(view.context).enqueue(request) }
            .onFailure { error -> Log.w(TAG, "外屏封面加载失败: $error") }
    }

    private class CoverPresentation(context: Context, display: Display) : Presentation(context, display) {
        var coverImageView: ImageView? = null
            private set

        /** 窗口真的在外屏上可见时系统会给焦点，这是判断「有没有真的显示出来」最可靠的日志。 */
        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            Log.d(TAG, "外屏封面窗口焦点：hasFocus=$hasFocus")
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = FrameLayout(context)
            root.setBackgroundColor(Color.BLACK)
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            root.addView(image)
            coverImageView = image
            setContentView(root)
        }
    }

    /** 界面上的开关，或全局设置里的调试键，任一开启即允许直连外屏。 */
    private fun isDirectDisplayAllowed(): Boolean = allowDirectDisplay || isDirectDisplayDebugEnabled()

    /** 调试开关：全局设置里的 talebook_cover_debug，用于在没有背屏区域的机型上直连副屏。 */
    private fun isDirectDisplayDebugEnabled(): Boolean = runCatching {
        Settings.Global.getInt(activity.contentResolver, DIRECT_DISPLAY_DEBUG_KEY, DIRECT_DISPLAY_DISABLED) == 1
    }.getOrDefault(false)

    private companion object {
        /** adb shell settings put global talebook_cover_debug 1 —— 允许在没有背屏区域时直连副屏显示。 */
        const val DIRECT_DISPLAY_DEBUG_KEY = "talebook_cover_debug"
        const val DIRECT_DISPLAY_DISABLED = 0

        // 内置副屏的 displayId 在展开/合上时会变，只能按小范围逐个取。
        val SECONDARY_DISPLAY_ID_RANGE = 0..7

        /**
         * 等外屏供电的轮询次数。屏幕电源状态变化主要由 DisplayListener 收到，
         * 这里只是兜底（有些系统只发 add/remove，不发 change）。
         */
        const val WAIT_RETRY_TIMES = 10
        const val WAIT_RETRY_INTERVAL_MS = 3_000L
    }
}

/**
 * 外屏电源的开关与记录。
 *
 * 本应用点亮的那块屏由本应用负责关电：先试系统的关电接口，不通就用 cmd display power-off，
 * 最后 power-reset。应用被强制停止时来不及下电，会把屏幕 id 记在本地，
 * 下次启动由 recoverAfterForceStop 补做。
 */
object CoverScreenPower {

    private const val PREFS_NAME = "cover_screen_power"
    private const val KEY_POWERED_DISPLAY = "powered_display_id"
    private const val NO_DISPLAY = -1

    /** 系统的关电接口名（都是 @SystemApi，按顺序试）。 */
    private val POWER_OFF_API_NAMES = setOf("setDisplayPowerMode", "powerDisplay")

    /** Display.POWER_MODE_OFF，这个常量不公开，按值传。 */
    private const val POWER_MODE_OFF = 0

    /** 自己执行命令的等待上限。 */
    private const val COMMAND_TIMEOUT_MS = 3_000L

    /** 下电之后复查电源状态的等待时间。 */
    private const val VERIFY_DELAY_MS = 1_500L

    /** Display.STATE_DIM，这个常量不公开，按值传。 */
    private const val STATE_DIM = 1

    /** Display.STATE_* 里算「带电」的取值。 */
    private val LIT_STATES = setOf(
        Display.STATE_ON,
        STATE_DIM,
        Display.STATE_DOZE,
        Display.STATE_DOZE_SUSPEND,
        Display.STATE_ON_SUSPEND
    )

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    /** Display.getState() 是 API 35；更早的系统返回 -1（读不到）。 */
    fun stateOf(display: Display): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return -1
        return runCatching { display.state }.getOrDefault(-1)
    }

    fun stateOf(context: Context, displayId: Int): Int = displayOf(context, displayId)?.let { stateOf(it) } ?: -1

    fun isLit(display: Display): Boolean = stateOf(display) in LIT_STATES

    /** 明确知道没电（读不到状态不算，那种情况该做什么还是做什么）。 */
    fun isOff(display: Display): Boolean = stateOf(display) == Display.STATE_OFF

    fun displayOf(context: Context, displayId: Int): Display? = runCatching {
        context.getSystemService(DisplayManager::class.java)?.getDisplay(displayId)
    }.getOrNull()

    /** 记下这块屏是我们点亮的：应用被强杀时才有东西可补。 */
    fun markPoweredUp(context: Context, displayId: Int) {
        prefs(context).edit().putInt(KEY_POWERED_DISPLAY, displayId).apply()
    }

    fun clearPoweredUp(context: Context) {
        prefs(context).edit().remove(KEY_POWERED_DISPLAY).apply()
    }

    /** 给指定屏幕下电：系统接口 → cmd display power-off → cmd display power-reset。 */
    fun powerOff(context: Context, displayId: Int) {
        clearPoweredUp(context)
        if (tryPowerOffApi(context, displayId)) {
            verifyPoweredOff(context, displayId)
            return
        }
        runDisplayCommand("power-off", displayId) { failure ->
            if (failure == null) {
                Log.d(TAG, "已给外屏下电 id=$displayId（命令 cmd display power-off）")
                verifyPoweredOff(context, displayId)
                return@runDisplayCommand
            }
            Log.w(TAG, "命令下电失败：$failure，改试 power-reset")
            runDisplayCommand("power-reset", displayId) { fallback ->
                if (fallback == null) {
                    Log.d(TAG, "已把外屏电源交回系统 id=$displayId（命令 cmd display power-reset）")
                    verifyPoweredOff(context, displayId)
                } else {
                    markPoweredUp(context, displayId)
                    Log.w(TAG, "外屏下电失败：$fallback，这块屏会亮到系统自己关；下次启动会再试一次")
                }
            }
        }
    }

    /** 上次进程被强杀时可能没来得及下电，下次启动补做；没有标记时什么都不做。 */
    fun recoverAfterForceStop(activity: Activity) {
        val displayId = prefs(activity).getInt(KEY_POWERED_DISPLAY, NO_DISPLAY)
        if (displayId == NO_DISPLAY) return
        val display = displayOf(activity, displayId)
        if (display == null) {
            clearPoweredUp(activity)
            Log.d(TAG, "补做下电跳过：id=$displayId 的屏幕已经不在了")
            return
        }
        if (display.displayId == runCatching { activity.display?.displayId }.getOrNull()) {
            clearPoweredUp(activity)
            Log.d(TAG, "补做下电跳过：id=$displayId 现在是当前屏幕")
            return
        }
        if (!isLit(display)) {
            clearPoweredUp(activity)
            Log.d(TAG, "补做下电跳过：id=$displayId 已经没有电（state=${stateOf(display)}）")
            return
        }
        Log.d(TAG, "补做上次留下的外屏下电 id=$displayId")
        powerOff(activity, display.displayId)
    }

    /** 在后台执行一次 cmd display <子命令> <id>；回调给出失败原因，null 表示成功。 */
    fun runDisplayCommand(subcommand: String, displayId: Int, onResult: (String?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val failure = runCatching {
                val process = Runtime.getRuntime().exec(
                    arrayOf("cmd", "display", subcommand, displayId.toString())
                )
                if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    "命令超时"
                } else {
                    val error = process.errorStream.bufferedReader().use { reader -> reader.readText() }.trim()
                    when {
                        process.exitValue() != 0 -> "退出码 ${process.exitValue()}"
                        error.isNotEmpty() -> error.lineSequence().firstOrNull()?.trim().orEmpty()
                        else -> null
                    }
                }
            }.getOrElse { error -> error.message ?: error.toString() }
            scope.launch { onResult(failure) }
        }
    }

    /** 下电之后回头看一眼，电源状态直接写进日志，不必只靠外部 dumpsys 判断。 */
    private fun verifyPoweredOff(context: Context, displayId: Int) {
        scope.launch {
            delay(VERIFY_DELAY_MS)
            val state = stateOf(context, displayId)
            when {
                state in LIT_STATES -> {
                    markPoweredUp(context, displayId)
                    Log.w(TAG, "下电后复查 id=$displayId state=$state：命令执行成功但屏幕还有电，下次启动会再试一次")
                }

                state < 0 -> Log.d(TAG, "下电后复查 id=$displayId：系统不给读电源状态")
                else -> Log.d(TAG, "下电后复查 id=$displayId state=$state：已经不带电")
            }
        }
    }

    /** 系统的关电接口（@SystemApi，一般不开放给三方应用），不通返回 false。 */
    private fun tryPowerOffApi(context: Context, displayId: Int): Boolean {
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return false
        val method = displayManager.javaClass.methods.firstOrNull { candidate ->
            candidate.name in POWER_OFF_API_NAMES &&
                candidate.parameterTypes.size >= 2 &&
                candidate.parameterTypes[0] == Int::class.javaPrimitiveType &&
                candidate.parameterTypes[1] == Int::class.javaPrimitiveType
        }
        if (method == null) {
            Log.d(TAG, "系统没有向应用开放关电接口（没找到 ${POWER_OFF_API_NAMES.joinToString()}）")
            return false
        }
        val outcome = runCatching {
            val args = arrayOfNulls<Any>(method.parameterTypes.size)
            args[0] = displayId
            args[1] = POWER_MODE_OFF
            for (index in 2 until method.parameterTypes.size) {
                args[index] = when (method.parameterTypes[index]) {
                    Boolean::class.javaPrimitiveType -> false
                    Long::class.javaPrimitiveType -> 0L
                    String::class.java -> "talebook_cover"
                    else -> null
                }
            }
            // 这些接口有的是 void（返回 null），有的返回 boolean；只有明确返回 false 才算失败。
            method.invoke(displayManager, *args) != false
        }
        if (outcome.getOrDefault(false)) {
            Log.d(TAG, "已给外屏下电 id=$displayId（系统接口 ${method.name}）")
            return true
        }
        Log.w(TAG, "系统关电接口调用失败：${outcome.exceptionOrNull()}")
        return false
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
