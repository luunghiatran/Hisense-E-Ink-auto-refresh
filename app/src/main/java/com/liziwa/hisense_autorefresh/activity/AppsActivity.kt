package com.liziwa.hisense_autorefresh.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.view.AppListAdapter
import com.liziwa.hisense_autorefresh.AppPreferences
import com.liziwa.hisense_autorefresh.EInkAccessibilityService
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.util.Utils
import com.liziwa.hisense_autorefresh.databinding.ActivityAppsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * 应用选择界面：复用同一列表，按 mode 区分：
 * - [MODE_MONITOR]          配置监控应用（写入 targetPackageName）
 * - [MODE_READING_WHITELIST] 阅读白名单（写入 readingWhitelist，忽略自动识别）
 * 保存时发送 ACTION_CONFIG_CHANGE 广播，通知无障碍服务刷新配置。
 */
class AppsActivity : AppCompatActivity(), View.OnClickListener, CoroutineScope {

    private lateinit var binding: ActivityAppsBinding
    private var appItems = mutableListOf<AppListAdapter.ListItem>()
    private lateinit var prefs: AppPreferences

    companion object {
        private const val MSG_UPDATE_APP_LIST = 101
        const val EXTRA_MODE = "extra_mode"
        const val MODE_MONITOR = "monitor"
        const val MODE_READING_WHITELIST = "whitelist"
    }

    /** 当前模式：monitor=监控应用配置，whitelist=阅读白名单配置 */
    private var mode: String = MODE_MONITOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_apps)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        job = Job()
        prefs = AppPreferences.getInstance(applicationContext)
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_MONITOR
        // 按模式设置标题：监控模式显示“监控应用列表”，忽略应用(白名单)模式显示“忽略应用列表”
        setTitle(if (mode == MODE_READING_WHITELIST) R.string.title_activity_apps_whitelist else R.string.title_activity_apps)
        XLog.d("AppsActivity: onCreate mode=$mode")

        binding.btnSave.setOnClickListener { this.onClick(it) }
        binding.btnBack.setOnClickListener { this.onClick(it) }

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = adapter
        getAllApps()
    }


    /** 异步加载已安装应用，按已选列表标记选中态（排除本应用自身）后刷新 UI */
    private fun getAllApps() {
        launch {
            val choiceApps = if (mode == MODE_READING_WHITELIST) {
                prefs.readingWhitelist?.split(",")?.filter({ it.isNotEmpty() })
            } else {
                prefs.targetPackageName?.split(",")?.filter({ it.isNotEmpty() })
            }
            XLog.d("getAllApps: mode=$mode, 已选=${choiceApps?.size}个")
            Utils.getLauncherApps(applicationContext)
                .filter { !packageName.equals(it.packageName) } // 排除本应用，避免自我监控
                .forEach { it ->
                    appItems.add(
                        AppListAdapter.ListItem(
                            it.name,
                            it.packageName,
                            it.icon,
                            choiceApps?.contains(it.packageName) ?: false
                        )
                    )
                }
            XLog.d("getAllApps: 加载完成，共 ${appItems.size} 个应用")
            myHandler.sendEmptyMessage(MSG_UPDATE_APP_LIST)
        }
    }

    private val adapter: AppListAdapter =
        AppListAdapter(appItems, object : AppListAdapter.OnItemCheckedChangeListener {
            override fun onItemCheckedChange(
                position: Int,
                item: AppListAdapter.ListItem,
                checked: Boolean
            ) {
                item.isChecked = checked
                adapter.notifyItemChanged(position)
            }
        })

    private var myHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == MSG_UPDATE_APP_LIST) {
                adapter.notifyDataSetChanged()
                binding.tvLoading.visibility = View.GONE
            }
        }
    }

    override fun onClick(p0: View) {
        when (p0) {
            binding.btnSave -> {
                // 汇总所有选中包名，逗号分隔
                val stringBuilder = StringBuilder()
                appItems.forEach {
                    if (it.isChecked) {
                        stringBuilder.append(it.pkg).append(",")
                    }
                }
                XLog.d("onClick: 保存 mode=$mode, 选中=${stringBuilder}")
                if (mode == MODE_READING_WHITELIST) {
                    prefs.readingWhitelist = stringBuilder.toString()
                } else {
                    prefs.targetPackageName = stringBuilder.toString()
                    // 监控模式：选中为空则退化为“监控所有应用”
                    prefs.monitorGlobal = TextUtils.isEmpty(stringBuilder.toString())
                }
                sendBroadcast(Intent(EInkAccessibilityService.Companion.ACTION_CONFIG_CHANGE))
                Toast.makeText(applicationContext, R.string.toast_save, Toast.LENGTH_SHORT).show()
            }

            binding.btnBack -> {
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
}