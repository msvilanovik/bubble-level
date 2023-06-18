package com.zipoapps.level.app

import android.graphics.Typeface.BOLD
import android.os.Bundle
import android.widget.TextView
import com.zipoapps.premiumhelper.R
import com.zipoapps.premiumhelper.ui.splash.PHSplashActivity


class SplashActivity : PHSplashActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val splashTitle: TextView = findViewById(R.id.ph_splash_title_text)
        val currentTypeFace = splashTitle.typeface
        splashTitle.setTypeface(currentTypeFace, BOLD)
    }
}