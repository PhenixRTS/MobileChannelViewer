/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.channels

import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.express.ChannelExpress
import com.phenixrts.suite.phenixcore.closedcaptions.PhenixClosedCaptionView
import com.phenixrts.suite.phenixcore.common.asPhenixChannels
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.repositories.channels.models.PhenixCoreChannel
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import timber.log.Timber

internal class PhenixChannelRepository(
    private val channelExpress: ChannelExpress,
    private val configuration: PhenixConfiguration
) {
    private val rawChannels = mutableListOf<PhenixCoreChannel>()

    private val _onError = MutableSharedFlow<PhenixError>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _onEvent = MutableSharedFlow<PhenixEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _channels = MutableSharedFlow<List<PhenixChannel>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val channels: SharedFlow<List<PhenixChannel>> = _channels
    val onError: SharedFlow<PhenixError> = _onError
    val onEvent: SharedFlow<PhenixEvent> = _onEvent

    fun joinAllChannels(channelAliases: List<String>) {
        channelAliases.forEachIndexed { index, channelAlias ->
            if (rawChannels.any { it.alias == channelAlias }) return
            val channel = PhenixCoreChannel(channelExpress, configuration, channelAlias)
            Timber.d("Joining channel: $channelAlias")
            channel.join(
                PhenixChannelConfiguration(
                    channelAlias = channelAlias,
                    streamToken = configuration.streamTokens.getOrNull(index)
                )
            )
            launchIO { channel.onUpdated.collect { _channels.tryEmit(rawChannels.asPhenixChannels()) } }
            launchIO { channel.onError.collect { _onError.tryEmit(it) } }
            rawChannels.add(channel)
        }
        _channels.tryEmit(rawChannels.asPhenixChannels())
    }

    fun joinChannel(config: PhenixChannelConfiguration) {
        val channelAlias = config.channelAlias
        if (rawChannels.any { it.alias == channelAlias }) return
        val channel = PhenixCoreChannel(channelExpress, configuration, channelAlias)
        channel.join(config)
        launchIO { channel.onUpdated.collect { _channels.tryEmit(rawChannels.asPhenixChannels()) } }
        launchIO { channel.onError.collect { _onError.tryEmit(it) } }
        rawChannels.add(channel)
        _channels.tryEmit(rawChannels.asPhenixChannels())
    }

    fun selectChannel(channelAlias: String, isSelected: Boolean) {
        rawChannels.find { it.alias == channelAlias }?.selectChannel(isSelected)
    }

    fun renderOnSurface(channelAlias: String, surfaceView: SurfaceView?) {
        rawChannels.find { it.alias == channelAlias }?.renderOnSurface(surfaceView)
    }

    fun renderOnImage(channelAlias: String, imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) {
        rawChannels.find { it.alias == channelAlias }?.renderOnImage(imageView, configuration)
    }

    fun setAudioEnabled(channelAlias: String, enabled: Boolean) {
        rawChannels.find { it.alias == channelAlias }?.setAudioEnabled(enabled)
    }

    fun createTimeShift(channelAlias: String, timestamp: Long) {
        rawChannels.find { it.alias == channelAlias }?.createTimeShift(timestamp)
    }

    fun startTimeShift(channelAlias: String, duration: Long) {
        rawChannels.find { it.alias == channelAlias }?.startTimeShift(duration)
    }

    fun seekTimeShift(channelAlias: String, offset: Long) {
        rawChannels.find { it.alias == channelAlias }?.seekTimeShift(offset)
    }

    fun playTimeShift(channelAlias: String) {
        rawChannels.find { it.alias == channelAlias }?.playTimeShift()
    }

    fun pauseTimeShift(channelAlias: String) {
        rawChannels.find { it.alias == channelAlias }?.pauseTimeShift()
    }

    fun stopTimeShift(channelAlias: String) {
        rawChannels.find { it.alias == channelAlias }?.stopTimeShift()
    }

    fun limitBandwidth(channelAlias: String, bandwidth: Long) {
        rawChannels.find { it.alias == channelAlias }?.limitBandwidth(bandwidth)
    }

    fun releaseBandwidthLimiter(channelAlias: String) {
        rawChannels.find { it.alias == channelAlias }?.releaseBandwidthLimiter()
    }

    fun subscribeToCC(channelAlias: String, closedCaptionView: PhenixClosedCaptionView) {
        rawChannels.find { it.alias == channelAlias }?.subscribeToCC(closedCaptionView)
    }

}
