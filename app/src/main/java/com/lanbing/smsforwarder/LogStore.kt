/*
 * 短信转发助手
 *
 * 著作权人：华昊科技有限公司
 * 开发者：王士辉
 *
 * Copyright (c) 2026 华昊科技有限公司. All rights reserved.
 * 联系邮箱：huahao@email.cn
 */

package com.lanbing.smsforwarder

import android.content.Context
import com.lanbing.smsforwarder.utils.AppendLogFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object LogStore {
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val writeExecutor = Executors.newSingleThreadExecutor()
    private var storage: AppendLogFile? = null

    @Synchronized private fun storage(context: Context): AppendLogFile {
        return storage ?: AppendLogFile(File(context.filesDir, Constants.LOG_FILE_NAME), Constants.MAX_LOG_ENTRIES).also { storage = it }
    }

    fun append(context: Context, text: String) {
        val time = Date()
        val application = context.applicationContext
        writeExecutor.execute {
            try {
                val body = if (text.length > Constants.MAX_LOG_LINE_LENGTH) text.take(Constants.MAX_LOG_LINE_LENGTH) + "…(截断)" else text
                storage(application).append("[${sdf.format(time)}] $body")
            } catch (t: Throwable) { t.printStackTrace() }
        }
    }

    fun readAll(context: Context): List<String> = try { storage(context).readAll() }
        catch (t: Throwable) { t.printStackTrace(); emptyList() }

    fun clear(context: Context) {
        val application = context.applicationContext
        writeExecutor.execute {
            try { storage(application).clear() } catch (t: Throwable) { t.printStackTrace() }
        }
    }

    fun latest(context: Context): String = try { storage(context).latest() ?: "暂无日志" }
        catch (t: Throwable) { t.printStackTrace(); "暂无日志" }
}
