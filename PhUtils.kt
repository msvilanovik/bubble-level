package com.zipoapps.level.utility

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.zipoapps.premiumhelper.Premium


class PhUtils {

    companion object {

        fun hasActivePurchase(): Boolean{
            return Premium.hasActivePurchase()
        }

        fun showInterstitialOnNextActivity(activity: Activity){
            Premium.Ads.showInterstitialAdOnNextActivity(activity)
        }

        fun shareMyApp(context: Context){
            Premium.Utils.shareMyApp(context)
        }

        fun showRateDialog(supportFragmentManager: FragmentManager){
            Premium.showRateDialog(supportFragmentManager)
        }

        fun onMainActivityBackPressed(activity: Activity): Boolean{
            return Premium.onMainActivityBackPressed(activity)
        }

        fun showHappyMomentOnNextActivity (activity: AppCompatActivity, delay: Int = 0){
            Premium.showHappyMomentOnNextActivity(activity, delay)
        }
    }
}