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
        // 获取日志存储路径
        val logPath = getExternalFilesDir("xlog")?.absolutePath
            ?: "${filesDir.absolutePath}/xlog"

        File(logPath).mkdirs()

        // 配置参数
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
            .backupStrategy(NeverBackupStrategy())
            .cleanStrategy(FileLastModifiedCleanStrategy(1000 * 60 * 60 * 24 * 7))
            .flattener(ClassicFlattener())
            .writer(SimpleWriter())
            .build()

        XLog.init(
            config,
            androidPrinter,
            filePrinter
        )
    }
}