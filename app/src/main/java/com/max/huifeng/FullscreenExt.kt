package com.max.huifeng

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 把 Activity 切到全屏（沉浸）模式：隐藏顶部状态栏与底部导航栏。
 * 内容延伸到系统栏区域。从屏幕顶部下滑可临时呼出系统栏。
 */
fun Activity.setFullscreen() {
    // 让内容延伸到状态栏/导航栏区域
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // 隐藏状态栏 + 导航栏；下滑手势可临时呼出
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
