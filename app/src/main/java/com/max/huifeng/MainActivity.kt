package com.max.huifeng

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFullscreen()
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.tv_body_feel).setOnClickListener {
            startActivity(Intent(this, BodyFeelActivity::class.java))
        }
    }
}
