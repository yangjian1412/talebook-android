package com.talebook.app.reader

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object ReaderBarsController {
    val barsVisible: MutableState<Boolean> = mutableStateOf(false)

    fun toggle() {
        barsVisible.value = !barsVisible.value
    }

    fun hide() {
        barsVisible.value = false
    }

    fun show() {
        barsVisible.value = true
    }
}
