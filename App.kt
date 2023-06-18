package com.zipoapps.level.app

import android.app.Application
import com.zipoapps.ads.config.adManagerConfig
import com.zipoapps.level.BuildConfig
import com.zipoapps.level.R
import com.zipoapps.premiumhelper.Premium
import com.zipoapps.premiumhelper.configuration.appconfig.PremiumHelperConfiguration
import com.zipoapps.premiumhelper.ui.rate.RateHelper



class App: Application() {

    override fun onCreate() {
        super.onCreate()

        setLightMode()
        initPremiumHelper()
    }

    private fun setLightMode(){
        Premium.Utils.setDayMode()
    }

    private fun initPremiumHelper(){
        val adConfig = adManagerConfig {
            bannerAd("ca-app-pub-4563216819962244/8300878148")
            interstitialAd("ca-app-pub-4563216819962244/8576457736")
            rewardedAd("ca-app-pub-4563216819962244/6193589553")
            nativeAd("ca-app-pub-4563216819962244/3324131055")
            exitBannerAd("ca-app-pub-4563216819962244/8300878148")
            exitNativeAd("ca-app-pub-4563216819962244/3324131055")
        }

        Premium.initialize(this, PremiumHelperConfiguration.Builder(BuildConfig.DEBUG)
            .mainActivityClass(MainActivity::class.java)
            .rateDialogMode(RateHelper.RateMode.VALIDATE_INTENT)
            .startLikeProActivityLayout(R.layout.activity_start_like_pro_x_to_close)
            .relaunchPremiumActivityLayout(R.layout.activity_relaunch_premium)
            .relaunchOneTimeActivityLayout(R.layout.activity_relaunch_premium_one_time)
            .adManagerConfiguration(adConfig)
            .useTestAds(BuildConfig.DEBUG)
            .setInterstitialCapping(20)
            .setHappyMomentCapping(120)
            .showExitConfirmationAds(true)
            .configureMainOffer("bubblelevel_premium_v1_100_trial_7d_yearly")
            .termsAndConditionsUrl(getString(R.string.zipoapps_terms_conditions))
            .privacyPolicyUrl(getString(R.string.zipoapps_privacy_policy))
            .preventAdFraud(true)
            .build())
    }
}