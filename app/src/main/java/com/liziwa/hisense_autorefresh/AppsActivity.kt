package com.liziwa.hisense_autorefresh

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.liziwa.hisense_autorefresh.databinding.ActivityAppsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Arrays
import kotlin.coroutines.CoroutineContext

class AppsActivity : AppCompatActivity(), View.OnClickListener, CoroutineScope {

    private val TAG = "AppsActivity"

    private lateinit var binding: ActivityAppsBinding
    private var appItems = mutableListOf<ListItem>()
    private lateinit var prefs: AppPreferences

    companion object {
        private const val MSG_UPDATE_APP_LIST = 101
    }

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

        binding.btnSave.setOnClickListener { this.onClick(it) }
        binding.btnBack.setOnClickListener { this.onClick(it) }

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = adapter
        getAllApps()
    }


    private fun getAllApps() {
        launch {
            val choiceApps = prefs.targetPackageName?.split(",")?.filter({ it.isNotEmpty() })
            AppListHelper.getLauncherApps(applicationContext).forEach { it ->
                appItems.add(
                    ListItem(
                        it.name,
                        it.packageName,
                        it.icon,
                        choiceApps?.contains(it.packageName) ?: false
                    )
                )
            }
            myHandler.sendEmptyMessage(MSG_UPDATE_APP_LIST)
        }
    }

    private val adapter: AppListAdapter =
        AppListAdapter(appItems, object : AppListAdapter.OnItemCheckedChangeListener {
            override fun onItemCheckedChange(position: Int, item: ListItem, checked: Boolean) {
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
                val stringBuilder = StringBuilder()
                appItems.forEach {
                    if (it.isChecked) {
                        stringBuilder.append(it.pkg).append(",")
                    }
                }
                Log.d(TAG, "onClick: save=$stringBuilder")
                prefs.targetPackageName = stringBuilder.toString()
                prefs.monitorGlobal = TextUtils.isEmpty(stringBuilder.toString())
                sendBroadcast(Intent(EInkAccessibilityService.ACTION_CONFIG_CHANGE))
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