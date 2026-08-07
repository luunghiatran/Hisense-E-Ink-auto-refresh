package com.liziwa.hisense_autorefresh.util

import com.elvishew.xlog.printer.file.naming.FileNameGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * XLog 文件名生成器：按“本地日期”生成日志文件名（每天一个 .txt，便于按天归档）。
 */
class DateFileNameGenerator : FileNameGenerator {

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun isFileNameChangeable(): Boolean {
        return true
    }

    override fun generateFileName(logLevel: Int, timestamp: Long): String? {
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(Date(timestamp)) + ".txt"
    }
}