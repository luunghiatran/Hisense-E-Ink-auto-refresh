package com.liziwa.hisense_autorefresh.activity

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.AppPreferences
import com.liziwa.hisense_autorefresh.service.EInkAccessibilityService
import com.liziwa.hisense_autorefresh.service.EInkRepeatedlyRefreshService
import com.liziwa.hisense_autorefresh.service.EInkTouchOverlayService
import com.liziwa.hisense_autorefresh.MyApp
import com.liziwa.hisense_autorefresh.util.PermissionHelper
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.util.Utils
import com.liziwa.hisense_autorefresh.databinding.ActivityMainBinding
import com.liziwa.hisense_autorefresh.util.NotificationUtils

/**
 * 主界面：展示/配置监控开关、阈值、监控范围与阅读白名单，并引导权限申请。
 */
class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private val PERMISSION_REQUEST_OVERLAY = 1001
    private var titleClickCount = 0

    private enum class Tab { REPEATEDLY, INTERACTION, DRAW_OVER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = AppPreferences.getInstance(applicationContext)

        setupListeners()
        NotificationUtils.getInstance(this).removeErrorNotification()
        
        showTab(Tab.REPEATEDLY)
        updateUI()
    }

    private fun setupListeners() {
        // Tab Headers
        binding.llMonitorRepeatedly.setOnClickListener(this)
        binding.llMonitorInteraction.setOnClickListener(this)
        binding.llMonitorDrawOver.setOnClickListener(this)

        // Title Bar Buttons
        binding.btnTest.setOnClickListener(this)
        binding.btnExit.setOnClickListener(this)

        // Accessibility Layout
        with(binding.layoutAccessibility) {
            btnMonitorOn.setOnClickListener(this@MainActivity)
            btnMonitorOff.setOnClickListener(this@MainActivity)
            btnGrantAccessibility.setOnClickListener(this@MainActivity)
            cbMonitorKey.setOnClickListener(this@MainActivity)
            cbMonitorGlobal.setOnClickListener(this@MainActivity)
            cbAutoDetectReading.setOnClickListener(this@MainActivity)
            btnMonitorList.setOnClickListener(this@MainActivity)
            btnReadingWhitelist.setOnClickListener(this@MainActivity)
            ibReadingWhitelistHelp.setOnClickListener(this@MainActivity)
        }

        // Repeatedly Layout
        with(binding.layoutRepeatedly) {
            btnRepeatedlyOn.setOnClickListener(this@MainActivity)
            btnRepeatedlyOff.setOnClickListener(this@MainActivity)
            btnGrantBattery.setOnClickListener(this@MainActivity)
            tvBatteryOptimizationStatus.setOnClickListener(this@MainActivity)
            tvNotificationsStatus.setOnClickListener(this@MainActivity)
            cbAutoStartBoot.setOnClickListener(this@MainActivity)
            cbHideBackgroundTask.setOnClickListener(this@MainActivity)
        }

        // Draw Over Layout
        with(binding.layoutDrawOver) {
            btnTouchOn.setOnClickListener(this@MainActivity)
            btnTouchOff.setOnClickListener(this@MainActivity)
            btnGrantOverlay.setOnClickListener(this@MainActivity)
        }

        binding.tvCustomTitle.setOnClickListener {
            titleClickCount++
            if (titleClickCount >= 10) {
                titleClickCount = 0
                val newMode = !prefs.debugMode
                prefs.debugMode = newMode
                MyApp.reinitXLog(applicationContext)
                XLog.i("MainActivity: Debug mode $newMode")
                Toast.makeText(this, if (newMode) R.string.toast_debug_on else R.string.toast_debug_off, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setTabStyling(layout: LinearLayout, textView: TextView, active: Boolean) {
        val (bgColor, textColor, typeface) = if (active) {
            Triple(Color.BLACK, Color.WHITE, Typeface.DEFAULT_BOLD)
        } else {
            Triple(Color.TRANSPARENT, Color.BLACK, Typeface.DEFAULT)
        }
        layout.setBackgroundColor(bgColor)
        textView.setTextColor(textColor)
        textView.typeface = typeface
    }

    private fun showTab(tab: Tab) {
        binding.layoutRepeatedly.root.visibility = if (tab == Tab.REPEATEDLY) View.VISIBLE else View.GONE
        binding.layoutAccessibility.root.visibility = if (tab == Tab.INTERACTION) View.VISIBLE else View.GONE
        binding.layoutDrawOver.root.visibility = if (tab == Tab.DRAW_OVER) View.VISIBLE else View.GONE

        setTabStyling(binding.llMonitorRepeatedly, binding.tvMonitorRepeatedly, tab == Tab.REPEATEDLY)
        setTabStyling(binding.llMonitorInteraction, binding.tvMonitorInteraction, tab == Tab.INTERACTION)
        setTabStyling(binding.llMonitorDrawOver, binding.tvMonitorDrawOver, tab == Tab.DRAW_OVER)
    }

    fun updateUI() {
        updateAccessibilityUI()
        updateRepeatedlyUI()
        updateDrawOverUI()
        updatePermissionButtonStatus()
    }

    private fun updateAccessibilityUI() {
        val acc = binding.layoutAccessibility
        val hasAcc = Utils.isAccessibilityServiceEnabled(applicationContext)
        prefs.serviceState = hasAcc

        val isAccRunning = EInkAccessibilityService.isRunning
        acc.btnMonitorOn.text = if (isAccRunning && prefs.serviceSwitch) getString(R.string.btn_apply) else getString(R.string.btn_on)
        acc.btnMonitorOff.isEnabled = hasAcc && prefs.serviceSwitch
        acc.tvMonitorStatus.text = if (prefs.serviceSwitch) getString(R.string.tv_status_active) else getString(R.string.tv_status_stop)

        if (!acc.etInterval.isFocused) acc.etInterval.setText(prefs.interval.toString())
        if (!acc.etDelay.isFocused) acc.etDelay.setText(prefs.delayTime.toString())
        if (!acc.etIgnore.isFocused) acc.etIgnore.setText(prefs.ignoreTime.toString())
        
        acc.cbMonitorKey.isChecked = prefs.monitorKey
        acc.cbAutoDetectReading.isChecked = prefs.autoDetectReading

        val monitorGlobal = acc.cbMonitorGlobal.tag as Boolean? ?: prefs.monitorGlobal
        acc.cbMonitorGlobal.isChecked = monitorGlobal || TextUtils.isEmpty(prefs.targetPackageName)
        acc.btnMonitorList.isEnabled = !monitorGlobal && !TextUtils.isEmpty(prefs.targetPackageName)
        updateIntervalVisibility(monitorGlobal)
    }

    private fun updateRepeatedlyUI() {
        val per = binding.layoutRepeatedly
        val isPerRunning = EInkRepeatedlyRefreshService.isRunning
        per.tvRepeatedlyMonitorStatus.text = if (prefs.repeatedlyServiceSwitch) getString(R.string.tv_status_active) else getString(R.string.tv_status_stop)
        per.btnRepeatedlyOn.text = if (isPerRunning && prefs.repeatedlyServiceSwitch) getString(R.string.btn_apply) else getString(R.string.btn_on)
        per.btnRepeatedlyOff.isEnabled = prefs.repeatedlyServiceSwitch

        if (!per.etPeriod.isFocused) {
            per.etPeriod.setText(prefs.periodRefresh.toString())
        }
        per.cbAutoStartBoot.isChecked = prefs.autoStartOnBoot
        per.cbHideBackgroundTask.isChecked = prefs.hideBackgroundTask
    }

    private fun updateDrawOverUI() {
        val draw = binding.layoutDrawOver
        val isTouchRunning = EInkTouchOverlayService.isRunning
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        draw.tvTouchStatus.text = if (prefs.touchServiceSwitch) getString(R.string.tv_status_active) else getString(R.string.tv_status_stop)
        draw.btnTouchOn.text = if (isTouchRunning && prefs.touchServiceSwitch) getString(R.string.btn_apply) else getString(R.string.btn_on)
        draw.btnTouchOff.isEnabled = hasOverlay && prefs.touchServiceSwitch

        if (!draw.etInterval.isFocused) draw.etInterval.setText(prefs.touchInterval.toString())
        if (!draw.etDelay.isFocused) draw.etDelay.setText(prefs.touchDelayTime.toString())
        if (!draw.etIgnore.isFocused) draw.etIgnore.setText(prefs.touchIgnoreTime.toString())
    }

    private fun updatePermissionButtonStatus() {
        val hasAcc = Utils.isAccessibilityServiceEnabled(applicationContext)
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        val hasBattery = PermissionHelper.hasIgnoringBatteryOptimizationsPermission(this)
        val hasNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        val grantedStr = getString(R.string.status_granted)
        val notGrantedStr = getString(R.string.status_not_granted)

        binding.layoutAccessibility.btnGrantAccessibility.text = if (hasAcc) grantedStr else notGrantedStr
        binding.layoutDrawOver.btnGrantOverlay.text = if (hasOverlay) grantedStr else notGrantedStr
        binding.layoutRepeatedly.btnGrantBattery.text = if (hasBattery) grantedStr else notGrantedStr
        binding.layoutRepeatedly.tvBatteryOptimizationStatus.text = if (hasBattery) grantedStr else notGrantedStr
        binding.layoutRepeatedly.tvNotificationsStatus.text = if (hasNotify) grantedStr else notGrantedStr
    }

    private fun updateIntervalVisibility(monitorAll: Boolean) {
        val v = if (monitorAll) View.VISIBLE else View.GONE
        with(binding.layoutAccessibility) {
            tvInterval.visibility = v
            etInterval.visibility = v
            tvDelay.visibility = v
            etDelay.visibility = v
        }
    }

    override fun onClick(p0: View) {
        when (p0.id) {
            binding.llMonitorRepeatedly.id -> showTab(Tab.REPEATEDLY)
            binding.llMonitorInteraction.id -> showTab(Tab.INTERACTION)
            binding.llMonitorDrawOver.id -> showTab(Tab.DRAW_OVER)

            R.id.btn_grant_accessibility -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            R.id.btn_grant_overlay -> PermissionHelper.requestOverlayPermission(this, PERMISSION_REQUEST_OVERLAY)
            R.id.btn_grant_battery -> PermissionHelper.requestIgnoreBatteryOptimizationsPermission(this)
            R.id.tv_battery_optimization_status -> if (!PermissionHelper.hasIgnoringBatteryOptimizationsPermission(this)) PermissionHelper.requestIgnoreBatteryOptimizationsPermission(this)
            R.id.tv_notifications_status -> if (!PermissionHelper.hasNotificationsPermission(this)) PermissionHelper.requestNotificationsPermission(this)

            R.id.btn_monitor_on -> {
                if (!checkAccessibilityPermission()) return
                if (saveSettings()) {
                    prefs.serviceSwitch = true
                    updateUI()
                    sendBroadcast(Intent(EInkAccessibilityService.ACTION_CONFIG_CHANGE))
                    Toast.makeText(this, R.string.toast_monitor_applied, Toast.LENGTH_SHORT).show()
                }
            }
            R.id.btn_monitor_off -> if (saveSettings()) {
                prefs.serviceSwitch = false
                updateUI()
                sendBroadcast(Intent(EInkAccessibilityService.ACTION_CONFIG_CHANGE))
            }

            R.id.btn_touch_on -> {
                if (!checkOverlayPermission()) return
                if (saveSettings()) {
                    prefs.touchServiceSwitch = true
                    updateUI()
                    ContextCompat.startForegroundService(this, Intent(this, EInkTouchOverlayService::class.java))
                    sendBroadcast(Intent(EInkTouchOverlayService.ACTION_CONFIG_CHANGE))
                    Toast.makeText(this, R.string.toast_touch_applied, Toast.LENGTH_SHORT).show()
                }
            }
            R.id.btn_touch_off -> if (saveSettings()) {
                prefs.touchServiceSwitch = false
                updateUI()
                stopService(Intent(this, EInkTouchOverlayService::class.java))
            }

            R.id.btn_repeatedly_on -> if (saveSettings()) {
                prefs.repeatedlyServiceSwitch = true
                updateUI()
                ContextCompat.startForegroundService(this, Intent(this, EInkRepeatedlyRefreshService::class.java))
                sendBroadcast(Intent(EInkRepeatedlyRefreshService.ACTION_CONFIG_CHANGE))
                Toast.makeText(this, R.string.toast_repeatedly_applied, Toast.LENGTH_SHORT).show()
            }
            R.id.btn_repeatedly_off -> if (saveSettings()) {
                prefs.repeatedlyServiceSwitch = false
                updateUI()
                stopService(Intent(this, EInkRepeatedlyRefreshService::class.java))
            }

            R.id.cb_monitor_global -> {
                val cb = binding.layoutAccessibility.cbMonitorGlobal
                if (cb.isChecked && !Utils.isAccessibilityServiceEnabled(applicationContext)) {
                    cb.isChecked = false
                    return
                }
                binding.layoutAccessibility.btnMonitorList.isEnabled = !cb.isChecked
                cb.tag = cb.isChecked
                updateIntervalVisibility(cb.isChecked)
            }

            R.id.btn_monitor_list -> startActivity(Intent(this, AppsActivity::class.java))
            R.id.btn_reading_whitelist -> startActivity(Intent(this, AppsActivity::class.java).apply { putExtra(AppsActivity.EXTRA_MODE, AppsActivity.MODE_READING_WHITELIST) })
            R.id.ib_reading_whitelist_help -> AlertDialog.Builder(this).setTitle(R.string.dialog_title_tip).setMessage(R.string.btn_reading_whitelist_hint).setPositiveButton(R.string.btn_confirm) { d, _ -> d.dismiss() }.show()

            binding.btnTest.id -> Utils.refreshScreen(applicationContext)
            binding.btnExit.id -> onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        if (!Utils.isAccessibilityServiceEnabled(applicationContext)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.section_title_accessibility)
                .setMessage(R.string.dialog_msg_accessibility)
                .setPositiveButton(R.string.btn_to_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
            return false
        }
        return true
    }

    private fun checkOverlayPermission(): Boolean {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.section_title_overlay)
                .setMessage(R.string.dialog_msg_overlay)
                .setPositiveButton(R.string.btn_to_settings) { _, _ ->
                    PermissionHelper.requestOverlayPermission(this, PERMISSION_REQUEST_OVERLAY)
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
            return false
        }
        return true
    }

    private fun saveSettings(): Boolean {
        val acc = binding.layoutAccessibility
        val per = binding.layoutRepeatedly
        val draw = binding.layoutDrawOver

        val interval = acc.etInterval.text.toString().toIntOrNull()
        val delay = acc.etDelay.text.toString().toIntOrNull()
        val ignore = acc.etIgnore.text.toString().toIntOrNull()
        val period = per.etPeriod.text.toString().toIntOrNull()
        val tInterval = draw.etInterval.text.toString().toIntOrNull()
        val tDelay = draw.etDelay.text.toString().toIntOrNull()
        val tIgnore = draw.etIgnore.text.toString().toIntOrNull()

        if (interval == null || delay == null || ignore == null || period == null ||
            tInterval == null || tDelay == null || tIgnore == null) {
            showErrorDialog()
            return false
        }

        if (acc.cbMonitorGlobal.isChecked) {
            prefs.interval = interval
            prefs.delayTime = delay
        }
        prefs.ignoreTime = ignore
        prefs.periodRefresh = period

        prefs.monitorKey = acc.cbMonitorKey.isChecked
        prefs.monitorGlobal = acc.cbMonitorGlobal.isChecked
        prefs.autoDetectReading = acc.cbAutoDetectReading.isChecked
        prefs.autoStartOnBoot = per.cbAutoStartBoot.isChecked
        prefs.hideBackgroundTask = per.cbHideBackgroundTask.isChecked

        prefs.touchInterval = tInterval
        prefs.touchDelayTime = tDelay
        prefs.touchIgnoreTime = tIgnore

        return true
    }

    private fun showErrorDialog() {
        AlertDialog.Builder(this).setTitle(R.string.error).setMessage(R.string.error_empty_config).setPositiveButton(R.string.btn_confirm) { d, _ -> d.dismiss() }.show()
    }

    override fun onResume() {
        super.onResume()
        requestRequiredPermissions()
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        toggleRecentsVisibility(prefs.hideBackgroundTask)
    }

    private fun toggleRecentsVisibility(hide: Boolean) {
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).appTasks.firstOrNull()?.setExcludeFromRecents(hide)
    }

    private fun requestRequiredPermissions() {
        if (!PermissionHelper.hasIgnoringBatteryOptimizationsPermission(this)) {
            if (prefs.permissionIgnoringBatteryOptimizations != 0) {
                PermissionHelper.requestIgnoreBatteryOptimizationsPermission(this)
                prefs.permissionIgnoringBatteryOptimizations = 0
            }
        }
    }
}
