package com.liziwa.hisense_autorefresh

import android.app.Application
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.writer.SimpleWriter
import com.liziwa.hisense_autorefresh.util.DateFileNameGenerator
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import java.io.File


/**
 * Application 入口：初始化日志系统（XLog）与通知实例。
 * 日志同时输出到 Logcat（AndroidPrinter）与文件（按天归档，保留 7 天）。
 */
class MyApp : Application() {

    companion object {
        private const val TAG = "AutoRefresh"
    }

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.getInstance(this)
        initXLog()
    }

    private fun initXLog() {
        // 日志存储路径：优先外部存储 xlog 目录，否则回退到内部 filesDir
        val logPath = getExternalFilesDir("xlog")?.absolutePath
            ?: "${filesDir.absolutePath}/xlog"

        File(logPath).mkdirs()

        // 日志格式：带线程信息、2 级调用栈、边框，便于定位问题
        val config = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL)
            .tag(TAG)
            .enableThreadInfo()
            .enableStackTrace(2)
            .enableBorder()
            .build()

        val androidPrinter = AndroidPrinter(true)
        val filePrinter = FilePrinter.Builder(logPath)
            .fileNameGenerator(DateFileNameGenerator())
            .backupStrategy(NeverBackupStrategy()) // 不备份历史日志
            .cleanStrategy(FileLastModifiedCleanStrategy(1000 * 60 * 60 * 24 * 7)) // 仅保留 7 天
            .flattener(ClassicFlattener())
            .writer(SimpleWriter())
            .build()

        XLog.init(
            config,
            androidPrinter,
            filePrinter
        )
        XLog.i("MyApp: XLog 初始化完成，路径=$logPath")
    }
}