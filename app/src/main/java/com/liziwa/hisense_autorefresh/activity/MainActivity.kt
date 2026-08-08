package com.liziwa.hisense_autorefresh.activity

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.AppPreferences
import com.liziwa.hisense_autorefresh.EInkAccessibilityService
import com.liziwa.hisense_autorefresh.MyApp
import com.liziwa.hisense_autorefresh.util.PermissionHelper
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.util.Utils
import com.liziwa.hisense_autorefresh.databinding.ActivityMainBinding
import com.liziwa.hisense_autorefresh.util.NotificationUtils

/**
 * 主界面：展示/配置监控开关、阈值、监控范围与阅读白名单，并引导权限申请。
 * 配置通过 SharedPreferences 持久化；保存时发送 ACTION_CONFIG_CHANGE 广播通知服务热更新。
 */
class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private val PERMISSION_REQUEST_OVERLAY = 1001
    private var titleClickCount = 0 // 主标题连点计数，达到阈值切换调试模式

    private var dialog: AlertDialog? = null

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

        binding.btnToAccessibilitySettings.setOnClickListener { this.onClick(it) }
        binding.btnMonitorStatusOn.setOnClickListener { this.onClick(it) }
        binding.btnMonitorStatusOff.setOnClickListener { this.onClick(it) }
        binding.cbMonitorTouch.setOnClickListener { this.onClick(it) }
        binding.cbMonitorKey.setOnClickListener { this.onClick(it) }
        binding.cbMonitorGlobal.setOnClickListener { this.onClick(it) }
        binding.cbAutoDetectReading.setOnClickListener { this.onClick(it) }
        binding.btnMonitorList.setOnClickListener { this.onClick(it) }
        binding.btnReadingWhitelist.setOnClickListener { this.onClick(it) }
        binding.ibReadingWhitelistHelp.setOnClickListener { this.onClick(it) }
        binding.btnSave.setOnClickListener { this.onClick(it) }
        binding.btnTest.setOnClickListener { this.onClick(it) }
        binding.btnExit.setOnClickListener { this.onClick(it) }
        NotificationUtils.getInstance(this).removeErrorNotification()

        // 主标题连点 10 次切换调试模式（默认关闭）：开启后 XLog 写文件并打开详细日志
        binding.tvCustomTitle.setOnClickListener {
            titleClickCount++
            if (titleClickCount >= 10) {
                titleClickCount = 0
                val newMode = !prefs.debugMode
                prefs.debugMode = newMode
                MyApp.reinitXLog(applicationContext)
                XLog.i("MainActivity: 调试模式切换为 $newMode")
                Toast.makeText(
                    this,
                    if (newMode) getString(R.string.toast_debug_on) else getString(R.string.toast_debug_off),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun updateUI() {
        val isAccessibilityServiceEnabled = Utils.isAccessibilityServiceEnabled(applicationContext)
        binding.tvAccessibilityStatus.text = if (isAccessibilityServiceEnabled) {
            prefs.serviceState = true
            getString(R.string.tv_status_active)
        } else {
            prefs.serviceState = false
            getString(R.string.tv_status_stop)
        }
        binding.tvMonitor.isEnabled = isAccessibilityServiceEnabled
        binding.tvMonitorStatus.isEnabled = isAccessibilityServiceEnabled
        binding.btnMonitorStatusOn.isEnabled = isAccessibilityServiceEnabled
        binding.btnMonitorStatusOff.isEnabled = isAccessibilityServiceEnabled
        binding.tvMonitorStatus.text = if (isAccessibilityServiceEnabled && prefs.serviceSwitch) {
            getString(R.string.tv_status_active)
        } else {
            getString(R.string.tv_status_stop)
        }
        binding.btnMonitorStatusOn.isEnabled = isAccessibilityServiceEnabled && !prefs.serviceSwitch
        binding.btnMonitorStatusOff.isEnabled = isAccessibilityServiceEnabled && prefs.serviceSwitch
        binding.etInterval.text =
            Editable.Factory.getInstance().newEditable(prefs.interval.toString())
        binding.etDelay.text =
            Editable.Factory.getInstance().newEditable(prefs.delayTime.toString())
        binding.etIgnore.text =
            Editable.Factory.getInstance().newEditable(prefs.ignoreTime.toString())
        binding.etPeriod.text =
            Editable.Factory.getInstance().newEditable(prefs.periodRefresh.toString())
        binding.cbMonitorTouch.isChecked = prefs.monitorTouch
        binding.cbMonitorKey.isChecked = prefs.monitorKey
        binding.cbAutoDetectReading.isChecked = prefs.autoDetectReading
        val monitorGlobal = binding.cbMonitorGlobal.tag as Boolean? ?: prefs.monitorGlobal
        binding.cbMonitorGlobal.isChecked =
            monitorGlobal || TextUtils.isEmpty(prefs.targetPackageName)
        binding.btnMonitorList.isEnabled =
            !monitorGlobal && !TextUtils.isEmpty(prefs.targetPackageName)
        binding.cbHideBackgroundTask.isChecked = prefs.hideBackgroundTask
    }


    override fun onClick(p0: View) {
        when (p0) {
            binding.btnToAccessibilitySettings -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            binding.btnMonitorStatusOn -> {
                if (prefs.permissionOverlay != 1) {
                    dialog =
                        PermissionHelper.requestOverlayPermission(this, PERMISSION_REQUEST_OVERLAY)
                    return
                }
                prefs.serviceSwitch = true
                updateUI()
                sendBroadcast(Intent(EInkAccessibilityService.Companion.ACTION_CONFIG_CHANGE))
            }

            binding.btnMonitorStatusOff -> {
                prefs.serviceSwitch = false
                updateUI()
                sendBroadcast(Intent(EInkAccessibilityService.Companion.ACTION_CONFIG_CHANGE))
            }

            binding.cbMonitorGlobal -> {
                binding.btnMonitorList.isEnabled = !binding.cbMonitorGlobal.isChecked
                binding.cbMonitorGlobal.tag = binding.cbMonitorGlobal.isChecked
            }

            binding.cbAutoDetectReading -> {
                // 仅记录勾选状态，保存时统一写入
            }

            binding.btnMonitorList -> {
                startActivity(Intent(this, AppsActivity::class.java))
            }

            binding.btnReadingWhitelist -> {
                startActivity(Intent(this, AppsActivity::class.java).apply {
                    putExtra(AppsActivity.EXTRA_MODE, AppsActivity.MODE_READING_WHITELIST)
                })
            }

            binding.ibReadingWhitelistHelp -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_title_tip)
                    .setMessage(R.string.btn_reading_whitelist_hint)
                    .setPositiveButton(R.string.btn_confirm) { dialog, which ->
                        dialog.dismiss()
                    }.show()
            }

            binding.btnSave -> {
                if (
                    TextUtils.isEmpty(binding.etInterval.text) ||
                    TextUtils.isEmpty(binding.etDelay.text) ||
                    TextUtils.isEmpty(binding.etIgnore.text) ||
                    TextUtils.isEmpty(binding.etPeriod.text)
                ) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.error_empty_config)
                        .setPositiveButton(R.string.btn_confirm) { dialog, which ->
                            dialog.dismiss()
                        }.show()
                    return
                }
                try {
                    prefs.interval = binding.etInterval.text.toString().toInt()
                    prefs.delayTime = binding.etDelay.text.toString().toInt()
                    prefs.ignoreTime = binding.etIgnore.text.toString().toInt()
                    prefs.periodRefresh = binding.etPeriod.text.toString().toInt()
                    prefs.monitorKey = binding.cbMonitorKey.isChecked
                    prefs.monitorTouch = binding.cbMonitorTouch.isChecked
                    prefs.monitorGlobal = binding.cbMonitorGlobal.isChecked
                    prefs.autoDetectReading = binding.cbAutoDetectReading.isChecked
                    binding.cbMonitorGlobal.tag = null
                    prefs.hideBackgroundTask = binding.cbHideBackgroundTask.isChecked
                    sendBroadcast(Intent(EInkAccessibilityService.Companion.ACTION_CONFIG_CHANGE))
                    Toast.makeText(applicationContext, R.string.toast_save, Toast.LENGTH_SHORT)
                        .show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    AlertDialog.Builder(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.error_empty_format)
                        .setPositiveButton(R.string.btn_confirm) { dialog, which ->
                            dialog.dismiss()
                        }.show()
                }
            }

            binding.btnTest -> {
                Utils.refreshScreen(applicationContext)
            }

            binding.btnExit -> onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        XLog.d("onResume: ")
        // 请求必要权限
        requestRequiredPermissions()
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        XLog.d("onPause: ")
        dialog?.dismiss()
        dialog = null
        toggleRecentsVisibility(prefs.hideBackgroundTask)
    }

    private fun toggleRecentsVisibility(hide: Boolean) {
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
            .appTasks
            .firstOrNull()
            ?.setExcludeFromRecents(hide)
    }

    /**
     * 进入前台时检查必要权限：忽略电池优化、悬浮窗。
     * 每项权限若未授权且未处于“已提示过”状态，则弹引导；悬浮窗缺失会临时关闭服务开关。
     * 用 prefs 中的权限标记位避免每次 onResume 重复弹窗。
     */
    private fun requestRequiredPermissions() {
        XLog.d("requestRequiredPermissions: 电池优化=${prefs.permissionIgnoringBatteryOptimizations}, 悬浮窗=${prefs.permissionOverlay}")

        // 检查忽略电池优化权限
        if (!PermissionHelper.hasIgnoringBatteryOptimizationsPermission(this)) {
            if (prefs.permissionIgnoringBatteryOptimizations != 0) {
                XLog.d("requestRequiredPermissions: 申请忽略电池优化权限")
                PermissionHelper.requestIgnoreBatteryOptimizationsPermission(this)
                prefs.permissionIgnoringBatteryOptimizations = 0
                return
            }
        } else {
            prefs.permissionIgnoringBatteryOptimizations = 1
        }

        //申请悬浮窗权限
        if (!PermissionHelper.hasOverlayPermission(this)) {
            if (prefs.permissionOverlay != 0) {
                XLog.d("requestRequiredPermissions: 申请悬浮窗权限，并临时关闭服务")
                dialog = PermissionHelper.requestOverlayPermission(this, PERMISSION_REQUEST_OVERLAY)
                prefs.permissionOverlay = 0
                prefs.serviceSwitch = false
                sendBroadcast(Intent(EInkAccessibilityService.Companion.ACTION_CONFIG_CHANGE))
                return
            }
        } else {
            prefs.permissionOverlay = 1
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_OVERLAY) {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                Toast.makeText(this, R.string.request_permission_overlay_error, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

}
