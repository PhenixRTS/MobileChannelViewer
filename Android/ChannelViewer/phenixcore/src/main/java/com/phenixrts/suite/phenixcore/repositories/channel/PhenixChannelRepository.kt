/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.channel

import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.ChannelExpress
import com.phenixrts.express.ExpressPublisher
import com.phenixrts.pcast.UserMediaStream
import com.phenixrts.room.RoomService
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.asPhenixChannels
import com.phenixrts.suite.phenixcore.common.launchIO
import com.phenixrts.suite.phenixcore.repositories.channel.models.PhenixCoreChannel
import com.phenixrts.suite.phenixcore.repositories.chat.PhenixChatRepository
import com.phenixrts.suite.phenixcore.repositories.core.common.getPublishToChannelOptions
import com.phenixrts.suite.phenixcore.repositories.models.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

internal class PhenixChannelRepository(
    private val channelExpress: ChannelExpress,
    private val configuration: PhenixConfiguration,
    private val chatRepository: PhenixChatRepository
) {
    private val pCastExpress = channelExpress.roomExpress!!.pCastExpress
    private val rawChannels = mutableListOf<PhenixCoreChannel>()
    private var publisher: ExpressPublisher? = null
    private var roomService: RoomService? = null
    private var channelConfiguration: PhenixChannelConfiguration? = null

    private val _onError = ConsumableSharedFlow<PhenixError>()
    private val _onEvent = ConsumableSharedFlow<PhenixEvent>()
    private val _channels = ConsumableSharedFlow<List<PhenixChannel>>(canReplay = true)

    val channels = _channels.asSharedFlow()
    val onError = _onError.asSharedFlow()
    val onEvent = _onEvent.asSharedFlow()

    fun joinAllChannels(channelAliases: List<String>) {
        if (configuration.channelStreamTokens.isNotEmpty() && configuration.channelStreamTokens.size != channelAliases.size) {
            _onError.tryEmit(PhenixError.JOIN_ROOM_FAILED)
            return
        }
        channelAliases.forEachIndexed { index, channelAlias ->
            if (hasChannel(channelAlias)) return
            val channel = PhenixCoreChannel(pCastExpress, channelExpress, configuration, chatRepository, channelAlias)
            val phenixChannelConfiguration = PhenixChannelConfiguration(
                channelAlias = channelAlias,
                streamToken = configuration.channelStreamTokens.getOrNull(index) ?: configuration.streamToken,
                publishToken = configuration.publishToken
            )
            joinChannel(channel, phenixChannelConfiguration)
        }
        _channels.tryEmit(rawChannels.asPhenixChannels())
    }

    fun joinChannel(phenixChannelConfiguration: PhenixChannelConfiguration) {
        val config = PhenixChannelConfiguration(
            channelAlias = phenixChannelConfiguration.channelAlias,
            channelID = phenixChannelConfiguration.channelID,
            streamToken = phenixChannelConfiguration.streamToken ?: configuration.streamToken,
            publishToken = phenixChannelConfiguration.publishToken ?: configuration.publishToken,
            channelCapabilities = phenixChannelConfiguration.channelCapabilities
        )
        channelConfiguration = config
        val channelAlias = config.channelAlias
        if (hasChannel(channelAlias)) return
        val channel = PhenixCoreChannel(pCastExpress, channelExpress, configuration, chatRepository, channelAlias!!)
        joinChannel(channel, config)
        _channels.tryEmit(rawChannels.asPhenixChannels())
    }

    fun publishToChannel(phenixChannelConfiguration: PhenixChannelConfiguration, userMediaStream: UserMediaStream) {
        channelConfiguration = phenixChannelConfiguration
        Timber.d("Publishing to channel: $phenixChannelConfiguration")
        _onEvent.tryEmit(PhenixEvent.PHENIX_CHANNEL_PUBLISHING)
        channelExpress.publishToChannel(
            getPublishToChannelOptions(configuration, phenixChannelConfiguration, userMediaStream)
        ) { status: RequestStatus?, service: RoomService?, expressPublisher: ExpressPublisher? ->
            Timber.d("Stream is published: $status")
            publisher = expressPublisher
            roomService = service
            if (status == RequestStatus.OK && roomService != null && publisher != null) {
                _onEvent.tryEmit(PhenixEvent.PHENIX_CHANNEL_PUBLISHED.apply { data = phenixChannelConfiguration })
            } else {
                _onError.tryEmit(PhenixError.PUBLISH_CHANNEL_FAILED.apply { data = phenixChannelConfiguration })
            }
        }
    }

    fun stopPublishingToChannel() {
        Timber.d("Stopping media publishing")
        publisher?.stop()
        publisher = null
        _onEvent.tryEmit(PhenixEvent.PHENIX_CHANNEL_PUBLISH_ENDED.apply { data = channelConfiguration })
    }

    fun selectChannel(channelAlias: String, isSelected: Boolean) {
        findChannel(channelAlias)?.selectChannel(isSelected)
    }

    fun renderOnSurface(channelAlias: String, surfaceView: SurfaceView?) {
        findChannel(channelAlias)?.renderOnSurface(surfaceView)
    }

    fun renderOnImage(channelAlias: String, imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) {
        findChannel(channelAlias)?.renderOnImage(imageView, configuration)
    }

    fun setAudioEnabled(channelAlias: String, enabled: Boolean) {
        findChannel(channelAlias)?.setAudioEnabled(enabled)
    }

    fun createTimeShift(channelAlias: String, timestamp: Long) {
        findChannel(channelAlias)?.createTimeShift(timestamp)
    }

    fun startTimeShift(channelAlias: String, duration: Long) {
        findChannel(channelAlias)?.startTimeShift(duration)
    }

    fun seekTimeShift(channelAlias: String, offset: Long) {
        findChannel(channelAlias)?.seekTimeShift(offset)
    }

    fun playTimeShift(channelAlias: String) {
        findChannel(channelAlias)?.playTimeShift()
    }

    fun pauseTimeShift(channelAlias: String) {
        findChannel(channelAlias)?.pauseTimeShift()
    }

    fun stopTimeShift(channelAlias: String) {
        findChannel(channelAlias)?.stopTimeShift()
    }

    fun limitBandwidth(channelAlias: String, bandwidth: Long) {
        findChannel(channelAlias)?.limitBandwidth(bandwidth)
    }

    fun releaseBandwidthLimiter(channelAlias: String) {
        findChannel(channelAlias)?.releaseBandwidthLimiter()
    }

    fun subscribeForMessages(channelAlias: String, configuration: PhenixMessageConfiguration) =
        findChannel(channelAlias)?.subscribeForMessages(configuration)

    fun leaveChannel(channelAlias: String) {
        findChannel(channelAlias)?.run {
            release()
            rawChannels.remove(this)
            _channels.tryEmit(rawChannels.asPhenixChannels())
        }
    }

    fun release() {
        rawChannels.forEach { it.release() }
        rawChannels.clear()
        _channels.tryEmit(rawChannels.asPhenixChannels())
    }

    private fun hasChannel(channelAlias: String?) = rawChannels.any { it.channelAlias == channelAlias }

    private fun findChannel(channelAlias: String) = rawChannels.find { it.channelAlias == channelAlias }

    private fun joinChannel(channel: PhenixCoreChannel, channelConfiguration: PhenixChannelConfiguration) {
        Timber.d("Joining channel: $channel, $channelConfiguration")
        channel.join(channelConfiguration)
        launchIO { channel.onUpdated.collect { _channels.tryEmit(rawChannels.asPhenixChannels()) } }
        launchIO { channel.onError.collect { _onError.tryEmit(it) } }
        rawChannels.add(channel)
    }

}
