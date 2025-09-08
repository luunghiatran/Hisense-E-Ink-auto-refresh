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
import com.liziwa.hisense_autorefresh.util.PermissionHelper
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.util.Utils
import com.liziwa.hisense_autorefresh.databinding.ActivityMainBinding
import com.liziwa.hisense_autorefresh.util.NotificationUtils

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private val PERMISSION_REQUEST_OVERLAY = 1001

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
        binding.btnMonitorList.setOnClickListener { this.onClick(it) }
        binding.btnSave.setOnClickListener { this.onClick(it) }
        binding.btnTest.setOnClickListener { this.onClick(it) }
        binding.btnExit.setOnClickListener { this.onClick(it) }
        NotificationUtils.getInstance(this).removeErrorNotification()
    }

    fun updateUI() {
        val isAccessibilityServiceEnabled = Utils.isAccessibilityServiceEnabled(applicationContext)
        binding.tvAccessibilityStatus.text = if (isAccessibilityServiceEnabled) {
            prefs.serviceState = true
            if (EInkAccessibilityService.Companion.SERVICE_CONNECT) {
                getString(R.string.tv_status_active)
            } else {
                getString(R.string.tv_status_error)
            }
        } else {
            prefs.serviceState = false
            getString(R.string.tv_status_stop)
        }
        binding.tvMonitor.isEnabled =
            isAccessibilityServiceEnabled && EInkAccessibilityService.Companion.SERVICE_CONNECT
        binding.tvMonitorStatus.isEnabled =
            isAccessibilityServiceEnabled && EInkAccessibilityService.Companion.SERVICE_CONNECT
        binding.btnMonitorStatusOn.isEnabled =
            isAccessibilityServiceEnabled && EInkAccessibilityService.Companion.SERVICE_CONNECT
        binding.btnMonitorStatusOff.isEnabled =
            isAccessibilityServiceEnabled && EInkAccessibilityService.Companion.SERVICE_CONNECT
        binding.tvMonitorStatus.text = if (isAccessibilityServiceEnabled && prefs.serviceSwitch) {
            getString(R.string.tv_status_active)
        } else {
            getString(R.string.tv_status_stop)
        }
        binding.btnMonitorStatusOn.isEnabled =
            isAccessibilityServiceEnabled && EInkAccessibilityService.Companion.SERVICE_CONNECT && !prefs.serviceSwitch
        binding.btnMonitorStatusOff.isEnabled =
            isAccessibilityServiceEnabled && EInkAccessibilityService.Companion.SERVICE_CONNECT && prefs.serviceSwitch
        binding.etInterval.text =
            Editable.Factory.getInstance().newEditable(prefs.interval.toString())
        binding.etDelay.text =
            Editable.Factory.getInstance().newEditable(prefs.delayTime.toString())
        binding.etIgnore.text =
            Editable.Factory.getInstance().newEditable(prefs.ignoreTime.toString())
        binding.cbMonitorTouch.isChecked = prefs.monitorTouch
        binding.cbMonitorKey.isChecked = prefs.monitorKey
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

            binding.btnMonitorList -> {
                startActivity(Intent(this, AppsActivity::class.java))
            }

            binding.btnSave -> {
                if (TextUtils.isEmpty(binding.etInterval.text) || TextUtils.isEmpty(binding.etDelay.text) || TextUtils.isEmpty(
                        binding.etIgnore.text
                    )
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
                    prefs.monitorKey = binding.cbMonitorKey.isChecked
                    prefs.monitorTouch = binding.cbMonitorTouch.isChecked
                    prefs.monitorGlobal = binding.cbMonitorGlobal.isChecked
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

    private fun requestRequiredPermissions() {

        // 检查忽略电池优化权限
        if (!PermissionHelper.hasIgnoringBatteryOptimizationsPermission(this)) {
            if (prefs.permissionIgnoringBatteryOptimizations != 0) {
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
                dialog = PermissionHelper.requestOverlayPermission(this, PERMISSION_REQUEST_OVERLAY)
                prefs.permissionOverlay = 0
                prefs.serviceSwitch = false;
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