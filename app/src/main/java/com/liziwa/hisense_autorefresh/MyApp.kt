package com.liziwa.hisense_autorefresh

import android.app.Application
import android.util.Log
import com.elvishew.xlog.BuildConfig
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.DefaultFlattener
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.ConsolePrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import com.elvishew.xlog.printer.file.writer.SimpleWriter
import java.io.File


class MyApp: Application() {
    private val TAG = "AutoRefresh"

    override fun onCreate() {
        super.onCreate()
        initXLog()
    }

    override fun onTerminate() {
        super.onTerminate()
    }

    private fun initXLog() {
        // 获取日志存储路径
        val logPath = getExternalFilesDir("xlog")?.absolutePath
            ?: "${filesDir.absolutePath}/xlog"

        // 确保目录存在
        File(logPath).mkdirs()

        // 加载原生库
        System.loadLibrary("c++_shared")
        System.loadLibrary("marsxlog")

        // 配置参数
        val config = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL)
            .tag(TAG)
            .enableThreadInfo()
            .enableStackTrace(2)
            .enableBorder()
            .build()

        val androidPrinter = AndroidPrinter(true)
        val consolePrinter = ConsolePrinter()
        val filePrinter = FilePrinter.Builder(logPath)
                .fileNameGenerator(DateFileNameGenerator())
                .backupStrategy(NeverBackupStrategy())
                .cleanStrategy(FileLastModifiedCleanStrategy(1000*60*60*24*7))
                .flattener(DefaultFlattener())
                .writer(SimpleWriter())
                .build()

        XLog.init(
            config,
            androidPrinter,
            consolePrinter,
            filePrinter);
    }
}