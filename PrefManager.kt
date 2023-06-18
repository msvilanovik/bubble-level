package com.zipoapps.level.utility

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity

const val PrefSound = "pref_sound_on_off"
const val PrefCustomCalibrate = "pref_custom_calibrate"
const val PrefShowLabels = "pref_show_labels"
const val PrefShowNorth = "pref_show_north"
const val PrefGeoNorth = "pref_geo_north"

fun Context.appPrefInstance(): SharedPreferences {
    return getSharedPreferences("${packageName}_preferences", AppCompatActivity.MODE_PRIVATE)
}

fun Context.appPrefPutValue(key: String, value: Boolean) {
    appPrefInstance().edit().putBoolean(key, value).apply()
}

fun Context.appPrefGetValue(key: String, defaultValue: Int): Int {
    return appPrefInstance().getInt(key, defaultValue)
}

fun Context.appPrefGetValue(key: String, defaultValue: Boolean): Boolean {
    return appPrefInstance().getBoolean(key, defaultValue)
}