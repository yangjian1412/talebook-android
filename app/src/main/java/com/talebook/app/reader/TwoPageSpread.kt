package com.talebook.app.reader

import android.content.res.Resources

/**
 * 「双页显示」什么时候能开。
 *
 * 只看阅读面有多宽（dp），不看横竖屏、也不管是不是折叠屏：
 * 一栏要 325dp 上下才宽松（中文一行二十五六字），所以两栏的硬槛是 650dp。
 * 这样直板手机竖屏（410dp 上下）、折叠屏外屏（420dp 上下）、还内屏偏窄的那些折叠屏
 * （600dp 上下的那些）都不开；手机横屏、平板、内屏更宽的才开。
 */
object TwoPageSpread {

    /** 阅读面至少要有这么宽才分两栏。 */
    const val MIN_WIDTH_DP = 650

    /** 这个宽度能不能开双页；界面上的开关用这个。 */
    fun isAvailable(widthDp: Int): Boolean = widthDp >= MIN_WIDTH_DP

    /** 要不要真的分两栏：用户开着，而且现在的宽度够。 */
    fun isActive(enabled: Boolean, widthDp: Int): Boolean = enabled && isAvailable(widthDp)

    /** 系统报出来的窗口宽度（dp）；还没布局过的时候用它。 */
    fun windowWidthDp(resources: Resources): Int {
        val reported = resources.configuration.screenWidthDp
        return if (reported > 0) reported else pxToDp(resources, resources.displayMetrics.widthPixels)
    }

    /** 像素换成 dp：WebView 里 CSS 的 px 就是这里的 dp，所以槛要按 dp 算。 */
    fun pxToDp(resources: Resources, widthPx: Int): Int =
        (widthPx / resources.displayMetrics.density + 0.5f).toInt()
}
