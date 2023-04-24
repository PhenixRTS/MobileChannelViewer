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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

private const val DATE_FORMAT = "MM-dd HH:mm:ss.SSS"
private val LEVEL_NAMES = arrayOf("F", "?", "T", "D", "I", "W", "E")
private const val LOG_FOLDER = "logs"
private const val SDK_LOG_FOLDER = "$LOG_FOLDER/sdk"
private const val APP_LOG_FOLDER = "$LOG_FOLDER/app"
private const val APP_LOGS_FILE = "Phenix_appLogs_"
private const val SDK_LOGS_FILE = "Phenix_sdkLogs_"
private const val FILE_EXTENSION = ".txt"
private const val MAX_LINES_PER_FILE = 1000 * 10 // 10k Lines per log file

class FileWriterDebugTree(
    private val context: Application,
    private val logTag: String
) : Timber.DebugTree() {

    private var sdkLogFolder: File? = null
    private var appLogFolder: File? = null
    private var sdkFileWriter: BufferedWriter? = null
    private var appFileWriter: BufferedWriter? = null
    private val sdkLogFiles = mutableSetOf<File>()
    private val appLogFiles = mutableSetOf<File>()
    private var sdkLogLineCount = 0
    private var appLogLineCount = 0
    private var logCollectionMethod: (((String) -> Unit) -> Unit)? = null

    private val writerChannel = Channel<String>(1)
    private val writerMutex = Mutex()


    init {
        initLogFolders()
        initLogFiles()
        writeAppLogs()
    }

    /**
     * Makes logged out class names clickable in Logcat
     */
    override fun createStackElementTag(element: StackTraceElement) =
        "$logTag: (${element.fileName}:${element.lineNumber}) #${element.methodName} "

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        formatAndLogMessage(tag, priority, message, t)
    }

    override fun log(priority: Int, message: String?, vararg args: Any?) {
        super.log(priority, message, *args)
        formatAndLogMessage(logTag, priority, message, null)
    }

    override fun log(priority: Int, t: Throwable?, message: String?, vararg args: Any?) {
        super.log(priority, t, message, *args)
        formatAndLogMessage(logTag, priority, message, t)
    }

    fun writeSdkLogs(onWritten: () -> Unit = {}) = try {
        logCollectionMethod?.invoke { logs ->
            sdkFileWriter?.write(logs)
            onWritten()
        }
    } catch (e: Exception) {
        e(e, "Failed to write sdk logs")
    }

    fun setLogCollectionMethod(method: ((String) -> Unit) -> Unit) {
        logCollectionMethod = method
    }

    fun collectLogs(appLogs: Boolean, onCollected: (List<String>) -> Unit) = launchIO {
        if (appLogs) {
            onCollected(collectAppLogs())
        } else {
            writeSdkLogs {
                onCollected(collectSdkLogs())
            }
        }
    }

    fun getLogFileUris(authority: String): Set<Uri> {
        sdkFileWriter?.flush()
        appFileWriter?.flush()
        val logFileUris = mutableSetOf<Uri>()
        sdkLogFiles.mapNotNullTo(logFileUris) { it.getUri(authority) }
        appLogFiles.mapNotNullTo(logFileUris) { it.getUri(authority) }
        return logFileUris
    }

    @Throws(Exception::class)
    fun clearLogs() {
        sdkFileWriter?.flush()
        appFileWriter?.flush()
        sdkLogFiles.forEach { it.delete() }
        appLogFiles.forEach { it.delete() }
        sdkLogFiles.clear()
        appLogFiles.clear()
        d("Log files deleted")
        initLogFiles()
    }

    private fun collectAppLogs(): List<String> = appLogFiles.flatMap { file -> file.useLines { it.toList() } }

    private fun collectSdkLogs(): List<String> = sdkLogFiles.flatMap { file -> file.useLines { it.toList() } }

    private fun File.getUri(authority: String) =
        if (length() > 0) FileProvider.getUriForFile(context, authority, this) else null

    private fun initLogFolders() {
        try {
            val contextWrapper = ContextWrapper(context)
            contextWrapper.createFolder(LOG_FOLDER)
            sdkLogFolder = contextWrapper.createFolder(SDK_LOG_FOLDER)
            appLogFolder = contextWrapper.createFolder(APP_LOG_FOLDER)
            if (sdkLogFolder == null || appLogFolder == null) {
                e("Failed to create log file directories: ${sdkLogFolder?.absolutePath}, ${appLogFolder?.absolutePath}")
                return
            }
            d("File writer initialized")
        } catch (e: Exception) {
            e(e, "Failed to initialize file writer")
            sdkLogLineCount = 0
            appLogLineCount = 0
        }
    }

    private fun initLogFiles() {
        try {
            sdkLogFolder!!.listFiles()?.let { sdkLogFiles.addAll(it) }
            appLogFolder!!.listFiles()?.let { appLogFiles.addAll(it) }

            val sdkLogFile = sdkLogFiles.lastOrNull()
                ?: sdkLogFolder!!.createLogFile(SDK_LOGS_FILE).apply { sdkLogFiles.add(this) }
            val appLogFile = appLogFiles.lastOrNull()
                ?: appLogFolder!!.createLogFile(APP_LOGS_FILE).apply { appLogFiles.add(this) }

            sdkFileWriter = BufferedWriter(FileWriter(sdkLogFile, true))
            appFileWriter = BufferedWriter(FileWriter(appLogFile, true))

            sdkLogLineCount = countLines(sdkLogFile)
            appLogLineCount = countLines(appLogFile)

            d("SDK log files: ${sdkLogFiles.size}, app log files: ${appLogFiles.size}")
        } catch (e: Exception) {
            e(e, "Failed to initialize file writer")
            sdkLogLineCount = 0
            appLogLineCount = 0
        }
    }

    private fun ContextWrapper.createFolder(name: String) =
        File(filesDir, name).apply { mkdir() }.takeIf { it.exists() }

    private fun File.createLogFile(name: String): File {
        val fileCount = list()?.size ?: 0
        return File(this, "$name$fileCount$FILE_EXTENSION")
    }

    private fun countLines(file: File) = try {
        val lineReader = LineNumberReader(BufferedReader(InputStreamReader(FileInputStream(file))))
        lineReader.skip(Long.MAX_VALUE)
        lineReader.lineNumber + 1
    } catch (e: Exception) {
        e(e, "Failed to count lines for file: ${file.absolutePath}")
        0
    }

    private fun writeAppLogs() = launchIO {
        while (true) {
            runCatching {
                val message = writerChannel.receive()
                writerMutex.withLock {
                    if (appLogLineCount == MAX_LINES_PER_FILE) {
                        appFileWriter?.flush()
                        appFileWriter?.close()
                        val appLogFile = appLogFolder!!.createLogFile(APP_LOGS_FILE)
                        appFileWriter = BufferedWriter(FileWriter(appLogFile, true))
                        appLogFiles.add(appLogFile)
                        appLogLineCount = 0
                    }
                    appFileWriter?.append(message + "\n")
                    appFileWriter?.flush()
                    appLogLineCount++
                }
            }.onFailure { throwable ->
                e(throwable, "Failed to write app logs")
            }
        }
    }

    private fun formatAndLogMessage(tag: String?, level: Int, message: String?, e: Throwable?) {
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
        val formattedMessage = builder.toString()
        writerChannel.trySend(formattedMessage)
    }
}
