package com.liziwa.hisense_autorefresh

import android.app.Application
import android.content.Context
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.writer.SimpleWriter
import com.liziwa.hisense_autorefresh.util.DateFileNameGenerator
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import java.io.File


/**
 * Application 入口：初始化日志系统（XLog）与通知实例。
 * 默认仅输出 Logcat；调试模式开启时追加文件输出并打开线程/调用栈/边框信息。
 */
class MyApp : Application() {

    companion object {
        private const val TAG = "AutoRefresh"
        private var filePrinter: FilePrinter? = null

        /**
         * 重新初始化 XLog（可重复调用，按当前调试模式切换配置）。
         * @param context 用于获取日志路径与配置
         * 调试模式决定 Logcat + 文件双输出或仅 Logcat。
         */
        fun reinitXLog(context: Context) {
            val prefs = AppPreferences.getInstance(context)
            val debug = prefs.debugMode

            // 日志存储路径：优先外部存储 xlog 目录，否则回退到内部 filesDir
            val logPath = context.getExternalFilesDir("xlog")?.absolutePath
                ?: "${context.filesDir.absolutePath}/xlog"
            File(logPath).mkdirs()

            // 调试模式：打开线程信息、2 级调用栈、边框，便于定位问题
            val configBuilder = LogConfiguration.Builder()
                .logLevel(LogLevel.ALL)
                .tag(TAG)
            if (debug) {
                configBuilder
                    .enableThreadInfo()
                    .enableStackTrace(2)
                    .enableBorder()
            }
            val config = configBuilder.build()

            // 基础输出：Logcat 常驻
            val printers = mutableListOf<Printer>(AndroidPrinter(true))
            // 调试模式：追加文件输出（按天归档，保留 7 天）；关闭时仅 Logcat，不写文件
            if (debug) {
                if (filePrinter == null) {
                    filePrinter = FilePrinter.Builder(logPath)
                        .fileNameGenerator(DateFileNameGenerator())
                        .backupStrategy(NeverBackupStrategy()) // 不备份历史日志
                        .cleanStrategy(FileLastModifiedCleanStrategy(1000 * 60 * 60 * 24 * 7)) // 仅保留 7 天
                        .flattener(ClassicFlattener())
                        .writer(SimpleWriter())
                        .build()
                }
                printers.add(filePrinter!!)
            }

            XLog.init(config, *printers.toTypedArray())
            XLog.i("MyApp: XLog 重新初始化完成, debugMode=$debug, 文件输出=$debug")
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.getInstance(this)
        reinitXLog(this)
    }
}