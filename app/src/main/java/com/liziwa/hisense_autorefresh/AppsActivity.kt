package com.liziwa.hisense_autorefresh

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.liziwa.hisense_autorefresh.databinding.ActivityAppsBinding
import java.util.Arrays

class AppsActivity : AppCompatActivity(), View.OnClickListener {

    private val TAG = "AppsActivity"

    private lateinit var binding: ActivityAppsBinding
    private var appItems = mutableListOf<ListItem>()
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_apps)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = AppPreferences.getInstance(applicationContext)

        binding.btnSave.setOnClickListener { this.onClick(it) }
        binding.btnBack.setOnClickListener { this.onClick(it) }

        val choiceApps = prefs.targetPackageName?.split(",")?.filter({ it.isNotEmpty() })

        AppListHelper.getLauncherApps(this).forEach { it ->
            appItems.add(ListItem(it.name, it.packageName, it.icon, choiceApps?.contains(it.packageName) ?: false))
        }

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = adapter
    }

    val adapter = AppListAdapter(appItems, object : AppListAdapter.OnItemCheckedChangeListener {
        override fun onItemCheckedChange(item: ListItem, checked: Boolean) {
            item.isChecked = checked
        }
    })

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
                sendBroadcast(Intent(EInkAccessibilityService.ACTION_CONFIG_CHANGE))
            }

            binding.btnBack -> {
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }
}