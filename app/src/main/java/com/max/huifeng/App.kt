package com.max.huifeng

import android.app.Application
import android.util.Log
import com.geely.aicarcontrolsdk.AiCarControlSDK

class App : Application() {

    companion object {
        private const val TAG = "HuifengApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application 启动，初始化 AiCarControlSDK...")

        // 在 Application 级别初始化 SDK，保证服务生命周期独立于 Activity
        AiCarControlSDK.init(this, needRetry = true, object : AiCarControlSDK.ConnectCallback {
            override fun onConnect(isConnected: Boolean) {
                Log.i(TAG, "SDK 连接状态: connected=$isConnected")
                if (!isConnected) {
                    Log.w(TAG, "SDK 连接断开，等待自动重连...")
                }
            }
        })
    }
}
