package com.zipoapps.level.setting

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zipoapps.level.R
import com.zipoapps.level.databinding.ActivitySettingsBinding
import com.zipoapps.level.utility.PhUtils
import com.zipoapps.level.utility.PrefGeoNorth
import com.zipoapps.level.utility.PrefShowNorth
import java.text.DecimalFormat

const val PrefSensitivity = "pref_sensitivity"
const val PrefInclinationAngle = "pref_inclination_angle"
const val PrefRotationAngle = "pref_rotation_angle"
const val PrefCoordinateSystem = "pref_coordinate_system"
const val PrefRate = "pref_rating"
const val prefShareApp = "pref_share_app"
const val PrefResetCalibration = "pref_reset_calibration"

class SettingsActivity : AppCompatActivity() {

    var hasChanges = false

    private val binding: ActivitySettingsBinding by lazy {
        ActivitySettingsBinding.inflate(layoutInflater)
    }

    private var settingFragment: SettingsFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = insets.top)
            settingFragment?.setBottomPadding(insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        settingFragment = SettingsFragment()

        if (savedInstanceState == null) {
            settingFragment?.let { settingsFragment ->
                supportFragmentManager
                    .beginTransaction()
                    .replace(binding.layoutSettings.id, settingsFragment)
                    .commit()
            }
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private var isPro: Boolean = false
        private var purchaseCategory: PreferenceCategory? = null

        fun setBottomPadding(bottomPadding: Int) {
            listView?.let {
                it.setPadding(0, 0, 0, bottomPadding)
                it.clipToPadding = false
            }
        }

        private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.setting, rootKey)
            isPro = PhUtils.hasActivePurchase()
            purchaseCategory = findPreference("purchase_cat")
            purchaseCategory?.isVisible = !isPro

            preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                when (key) {
                    PrefInclinationAngle -> {
                        findPreference<ListPreference>(key)?.summary = sharedPreferences.getString(key, getString(R.string.default_inclination_angle))
                        onChangeEvent()
                    }
                    PrefRotationAngle -> {
                        findPreference<ListPreference>(key)?.summary = sharedPreferences.getString(key, getString(R.string.default_rotation_angle))
                        onChangeEvent()
                    }
                    PrefCoordinateSystem -> {
                        findPreference<ListPreference>(key)?.summary = sharedPreferences.getString(key, getString(R.string.default_coordinate_system))
                        onChangeEvent()
                    }
                    PrefShowNorth -> {
                        val prefShowNorth = sharedPreferences.getBoolean(key, false)
                        findPreference<SwitchPreferenceCompat>(PrefGeoNorth)?.isEnabled = prefShowNorth
                        findPreference<SwitchPreferenceCompat>(PrefGeoNorth)?.isChecked = if (!prefShowNorth) false
                        else sharedPreferences?.getBoolean(PrefGeoNorth, false) ?: false
                        onChangeEvent()
                    }
                }
            }

            findPreference<SeekBarPreference>(PrefSensitivity)?.let { seekBar ->
                seekBar.title = String.format(
                    getString(R.string.sensitivity_title),
                    DecimalFormat("00.00").format(preferenceScreen.sharedPreferences?.getInt(PrefSensitivity, resources.getInteger(R.integer.sensitivity_range_default)))
                )
                seekBar.setOnPreferenceChangeListener { _, values ->
                    seekBar.title = String.format(getString(R.string.sensitivity_title), DecimalFormat("00.00").format(values))
                    onChangeEvent()
                    true
                }
            }

            findPreference<Preference>(PrefResetCalibration)?.setOnPreferenceClickListener {

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.reset_calibration_title))
                    .setMessage(getString(R.string.dialog_reset_message))
                    .setPositiveButton(getString(R.string.dialog_clear_all)) { dialog, _ ->
                        onChangeEvent()
                        dialog.dismiss()
                        try {
                            requireContext().getSharedPreferences("app.MainActivity", Context.MODE_PRIVATE).edit().clear().apply()
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                    .setNegativeButton(getString(R.string.dialog_not_yet), null)
                    .show()
                true
            }

            findPreference<Preference>(PrefRate)?.setOnPreferenceClickListener {
                PhUtils.showRateDialog(parentFragmentManager)
                true
            }

            findPreference<Preference>(prefShareApp)?.setOnPreferenceClickListener {
                PhUtils.shareMyApp(requireContext())
                true
            }

            findPreference<SwitchPreferenceCompat>("pref_show_labels")?.setOnPreferenceChangeListener { preference, newValue ->
                onChangeEvent()
                true
            }

            findPreference<SwitchPreferenceCompat>("pref_show_north")?.setOnPreferenceChangeListener { preference, newValue ->
                onChangeEvent()
                true
            }

            findPreference<SwitchPreferenceCompat>("pref_geo_north")?.setOnPreferenceChangeListener { preference, newValue ->
                onChangeEvent()
                true
            }

        }

        override fun onResume() {
            super.onResume()
            PhUtils.hasActivePurchase().let {
                if(isPro != it){
                    isPro = it
                    purchaseCategory?.isVisible = !isPro
                }
            }

            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

            findPreference<ListPreference>(PrefInclinationAngle)?.summary = preferenceScreen.sharedPreferences?.getString(
                PrefInclinationAngle, getString(R.string.default_inclination_angle)
            )

            findPreference<ListPreference>(PrefRotationAngle)?.summary = preferenceScreen.sharedPreferences?.getString(
                PrefRotationAngle, getString(R.string.default_rotation_angle)
            )

            findPreference<ListPreference>(PrefCoordinateSystem)?.summary = preferenceScreen.sharedPreferences?.getString(
                PrefCoordinateSystem, getString(R.string.default_coordinate_system)
            )
            val prefShowNorth = preferenceScreen.sharedPreferences?.getBoolean(PrefShowNorth, false) ?: false
            findPreference<SwitchPreferenceCompat>(PrefGeoNorth)?.isEnabled = prefShowNorth
            findPreference<SwitchPreferenceCompat>(PrefGeoNorth)?.isChecked = if (!prefShowNorth) false
            else preferenceScreen.sharedPreferences?.getBoolean(PrefGeoNorth, false) ?: false
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        }

        private fun onChangeEvent(){
            (requireActivity() as SettingsActivity).hasChanges = true
        }
    }


    override fun onBackPressed() {
        if(hasChanges){
            PhUtils.showHappyMomentOnNextActivity(this, 500)
        }else {
            PhUtils.showInterstitialOnNextActivity(this)
        }
        setResult(Activity.RESULT_OK)
        finish()
    }
}