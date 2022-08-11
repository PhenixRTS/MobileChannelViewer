/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.stream

import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.express.PCastExpress
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.asPhenixStreams
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.repositories.stream.models.PhenixCoreStream
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

internal class PhenixStreamRepository(
    private val pCastExpress: PCastExpress,
    private val configuration: PhenixConfiguration
) {
    private val rawStreams = mutableListOf<PhenixCoreStream>()
    private var streamConfiguration: PhenixStreamConfiguration? = null

    private val _onError = ConsumableSharedFlow<PhenixError>()
    private val _onEvent = ConsumableSharedFlow<PhenixEvent>()
    private val _streams = ConsumableSharedFlow<List<PhenixStream>>(canReplay = true)

    val streams = _streams.asSharedFlow()
    val onError = _onError.asSharedFlow()
    val onEvent = _onEvent.asSharedFlow()

    fun joinAllStreams(streamIDs: List<String>) {
        if (configuration.channelStreamTokens.isNotEmpty() && configuration.channelStreamTokens.size != streamIDs.size) {
            _onError.tryEmit(PhenixError.JOIN_ROOM_FAILED)
            return
        }
        streamIDs.forEachIndexed { index, streamID ->
            if (hasStream(streamID)) return
            val stream = PhenixCoreStream(pCastExpress,  configuration, streamID)
            val phenixStreamConfiguration = PhenixStreamConfiguration(
                streamID = streamID,
                streamToken = configuration.channelStreamTokens.getOrNull(index) ?: configuration.streamToken
            )
            joinStream(stream, phenixStreamConfiguration)
        }
        _streams.tryEmit(rawStreams.asPhenixStreams())
    }

    fun joinStream(phenixStreamConfiguration: PhenixStreamConfiguration) {
        val config = PhenixStreamConfiguration(
            streamID = phenixStreamConfiguration.streamID,
            streamToken = phenixStreamConfiguration.streamToken ?: configuration.streamToken,
            capabilities = phenixStreamConfiguration.capabilities
        )
        streamConfiguration = config
        val streamID = config.streamID
        if (hasStream(streamID)) return
        val stream = PhenixCoreStream(pCastExpress, configuration, streamID!!)
        joinStream(stream, config)
        _streams.tryEmit(rawStreams.asPhenixStreams())
    }

    fun selectStream(id: String, isSelected: Boolean) {
        findStream(id)?.selectStream(isSelected)
    }

    fun renderOnSurface(id: String, surfaceView: SurfaceView?) {
        findStream(id)?.renderOnSurface(surfaceView)
    }

    fun renderOnImage(id: String, imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) {
        findStream(id)?.renderOnImage(imageView, configuration)
    }

    fun setAudioEnabled(id: String, enabled: Boolean) {
        findStream(id)?.setAudioEnabled(enabled)
    }

    fun createTimeShift(id: String, timestamp: Long) {
        findStream(id)?.createTimeShift(timestamp)
    }

    fun startTimeShift(id: String, duration: Long) {
        findStream(id)?.startTimeShift(duration)
    }

    fun seekTimeShift(id: String, offset: Long) {
        findStream(id)?.seekTimeShift(offset)
    }

    fun playTimeShift(id: String) {
        findStream(id)?.playTimeShift()
    }

    fun pauseTimeShift(id: String) {
        findStream(id)?.pauseTimeShift()
    }

    fun stopTimeShift(id: String) {
        findStream(id)?.stopTimeShift()
    }

    fun leaveStream(streamID: String) {
        findStream(streamID)?.run {
            release()
            rawStreams.remove(this)
            _streams.tryEmit(rawStreams.asPhenixStreams())
        }
    }

    fun release() {
        rawStreams.forEach { it.release() }
        rawStreams.clear()
        _streams.tryEmit(rawStreams.asPhenixStreams())
    }

    private fun hasStream(streamID: String?) = rawStreams.any { it.streamID == streamID }

    private fun findStream(streamID: String) = rawStreams.find { it.streamID == streamID }

    private fun joinStream(stream: PhenixCoreStream, streamConfiguration: PhenixStreamConfiguration) {
        Timber.d("Joining stream: $stream, $streamConfiguration")
        stream.join(streamConfiguration)
        launchIO { stream.onUpdated.collect { _streams.tryEmit(rawStreams.asPhenixStreams()) } }
        launchIO { stream.onError.collect { _onError.tryEmit(it) } }
        rawStreams.add(stream)
    }

}
