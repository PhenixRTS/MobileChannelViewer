/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcommon.common

import android.app.Application
import android.content.ContextWrapper
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

private const val DATE_FORMAT = "MM-dd HH:mm:ss.SSS"
private val LEVEL_NAMES = arrayOf("F", "?", "T", "D", "I", "W", "E")

class FileWriterDebugTree(
    private val context: Application,
    private val logTag: String,
    private val providerAuthority: String
) : Timber.DebugTree() {

    private var filePath: File? = null
    private var sdkFileWriter: BufferedWriter? = null
    private var appFileWriter: BufferedWriter? = null
    private var sdkLogFile: File? = null
    private var firstAppLogFile: File? = null
    private var secondAppLogFile: File? = null
    private var lineCount = 0
    private var isUsingFirstFile = true

    init {
        initFileWriter()
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        launchIO {
            getFormattedLogMessage(tag, priority, message, t).run {
                writeAppLogs(this)
            }
        }
    }

    override fun log(priority: Int, message: String?, vararg args: Any?) {
        super.log(priority, message, *args)
        launchIO {
            getFormattedLogMessage(logTag, priority, message, null).run {
                writeAppLogs(this)
            }
        }
    }

    override fun log(priority: Int, t: Throwable?, message: String?, vararg args: Any?) {
        super.log(priority, t, message, *args)
        launchIO {
            getFormattedLogMessage(logTag, priority, message, t).run {
                writeAppLogs(this)
            }
        }
    }

    /**
     * Makes logged out class names clickable in Logcat
     */
    override fun createStackElementTag(element: StackTraceElement) =
        "$logTag: (${element.fileName}:${element.lineNumber}) #${element.methodName} "

    private fun initFileWriter() {
        val contextWrapper = ContextWrapper(context)
        filePath = File(contextWrapper.filesDir, LOG_FOLDER)
        if (filePath?.exists() == false && filePath?.mkdir() == false) {
            d("Failed to create log file directory")
            return
        }

        sdkLogFile = File(filePath, SDK_LOGS_FILE)
        firstAppLogFile = File(filePath, FIRST_APP_LOGS_FILE)
        secondAppLogFile = File(filePath, SECOND_APP_LOGS_FILE)

        try {
            sdkLogFile?.let { file ->
                sdkFileWriter = BufferedWriter(FileWriter(file, false))
            }
            firstAppLogFile?.let { file ->
                appFileWriter = BufferedWriter(FileWriter(file, true))
                getLineCount(file)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            lineCount = 0
        }
    }

    private fun getLineCount(file: File) {
        lineCount = try {
            val lineReader = LineNumberReader(BufferedReader(InputStreamReader(FileInputStream(file))))
            lineReader.skip(Long.MAX_VALUE)
            lineReader.lineNumber + 1
        } catch (e: IOException) {
            e.printStackTrace()
            0
        }
    }

    private fun writeAppLogs(message: String) = try {
        if (lineCount == MAX_LINES_PER_FILE) {
            appFileWriter?.flush()
            appFileWriter?.close()
            if (isUsingFirstFile) {
                isUsingFirstFile = false
                secondAppLogFile?.let { file ->
                    appFileWriter = BufferedWriter(FileWriter(file, false))
                }
            } else {
                firstAppLogFile?.let { file ->
                    appFileWriter = BufferedWriter(FileWriter(file, false))
                }
            }
            lineCount = 0
        }
        appFileWriter?.append(message + "\n")
        lineCount++
    } catch (e: Exception) {
        d(e, "Failed to write app logs")
    }

    fun writeSdkLogs(message: String) = try {
        sdkFileWriter?.write(message)
    } catch (e: Exception) {
        d(e, "Failed to write sdk logs")
    }

    private fun getFormattedLogMessage(tag: String?, level: Int, message: String?, e: Throwable?): String {
        val id: Long = try {
            Process.myTid().toLong()
        } catch (e1: RuntimeException) {
            Thread.currentThread().id
        }
        val builder: StringBuilder = StringBuilder()
            .append(SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date()))
            .append(' ').append(id)
            .append(' ').append(LEVEL_NAMES[level])
            .append(' ').append(tag)
            .append(' ').append(message)
        if (e != null) {
            builder.append(": throwable=").append(Log.getStackTraceString(e))
        }
        return builder.toString()
    }

    fun getLogFileUris(): List<Uri> {
        val fileUris = arrayListOf<Uri>()
        sdkFileWriter?.flush()
        appFileWriter?.flush()
        sdkLogFile?.takeIf { it.length() > 0 }?.let { file ->
            FileProvider.getUriForFile(context, providerAuthority, file)
        }?.let { sdkUri ->
            fileUris.add(sdkUri)
        }
        firstAppLogFile?.takeIf { it.length() > 0 }?.let { file ->
            FileProvider.getUriForFile(context, providerAuthority, file)
        }?.let { appUri ->
            fileUris.add(appUri)
        }
        secondAppLogFile?.takeIf { it.length() > 0 }?.let { file ->
            FileProvider.getUriForFile(context, providerAuthority, file)
        }?.let { appUri ->
            fileUris.add(appUri)
        }
        return fileUris
    }

    companion object {
        private const val LOG_FOLDER = "logs"
        private const val FIRST_APP_LOGS_FILE = "Phenix_appLogs_1.txt"
        private const val SECOND_APP_LOGS_FILE = "Phenix_appLogs_2.txt"
        private const val SDK_LOGS_FILE = "Phenix_sdkLogs.txt"
        private const val MAX_LINES_PER_FILE = 1000 * 10 // 10k Lines per log file
    }
}
