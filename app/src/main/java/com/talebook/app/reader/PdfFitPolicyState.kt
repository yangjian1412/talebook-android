package com.talebook.app.reader

import com.github.barteksc.pdfviewer.util.FitPolicy
import org.readium.r2.navigator.preferences.Fit

object PdfFitPolicyState {
    @Volatile
    @JvmStatic
    var policy: FitPolicy = FitPolicy.WIDTH

    fun fitFor(scrollMode: Boolean, isLandscape: Boolean): Fit? =
        when {
            scrollMode -> null
            isLandscape -> Fit.CONTAIN
            else -> Fit.WIDTH
        }

    fun update(scrollMode: Boolean, isLandscape: Boolean) {
        policy = if (!scrollMode && isLandscape) FitPolicy.HEIGHT else FitPolicy.WIDTH
    }
}
