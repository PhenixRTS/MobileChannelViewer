/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.room

import android.view.SurfaceView
import com.phenixrts.chat.RoomChatService
import com.phenixrts.chat.RoomChatServiceFactory
import com.phenixrts.common.Disposable
import com.phenixrts.express.*
import com.phenixrts.pcast.UserMediaStream
import com.phenixrts.room.*
import com.phenixrts.suite.phenixcore.common.*
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.core.common.*
import com.phenixrts.suite.phenixcore.repositories.core.common.getPublishOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.getPublishToRoomOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.getRoomOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.publishToRoom
import com.phenixrts.suite.phenixcore.repositories.models.*
import com.phenixrts.suite.phenixcore.repositories.room.models.PhenixCoreMember
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.util.*

// SDK issue - cannot reliable create chat service right after joining room
private const val CHAT_SERVICE_DELAY = 2000L

internal class PhenixRoomRepository(
    private val roomExpress: RoomExpress
) {

    private var userMediaStream: UserMediaStream? = null
    private val rawMembers = mutableListOf<PhenixCoreMember>()
    private val rawMessages = mutableListOf<PhenixMessage>()
    private val chatServices = mutableListOf<Pair<RoomChatService, List<String>>>()
    private var roomConfiguration: PhenixRoomConfiguration? = null

    private val _onError = MutableSharedFlow<PhenixError>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _onEvent = MutableSharedFlow<PhenixEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _members = MutableSharedFlow<List<PhenixMember>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _messages = MutableSharedFlow<List<PhenixMessage>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var _memberCount = MutableSharedFlow<Long>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val currentRoomType get() = roomService?.observableActiveRoom?.value?.observableType?.value
    private val disposables: MutableList<Disposable?> = mutableListOf()
    private val joinedDate = Date()

    private var selfCoreMember: PhenixCoreMember? = null
    private var publisher: ExpressPublisher? = null
    private var roomService: RoomService? = null

    val members: SharedFlow<List<PhenixMember>> = _members
    val messages: SharedFlow<List<PhenixMessage>> = _messages
    val onError: SharedFlow<PhenixError> = _onError
    val onEvent: SharedFlow<PhenixEvent> = _onEvent
    val memberCount: SharedFlow<Long> = _memberCount

    init {
        roomExpress.pCastExpress.getUserMedia { userMedia ->
            userMediaStream = userMedia
        }
    }

    fun joinRoom(configuration: PhenixRoomConfiguration) {
        _onEvent.tryEmit(PhenixEvent.PHENIX_ROOM_JOINING)
        Timber.d("Joining room with configuration: $configuration")
        roomExpress.joinRoom(configuration) { service ->
            roomService = service
            if (roomService == null) {
                _onError.tryEmit(PhenixError.JOIN_ROOM_FAILED)
            }
            launchMain {
                delay(CHAT_SERVICE_DELAY)
                // This is called 2 seconds after joinRoom callback is returned
                roomService?.let { service ->
                    roomConfiguration = configuration
                    selfCoreMember = PhenixCoreMember(service.self, true).apply {
                        if (configuration.memberRole == MemberRole.MODERATOR) {
                            createSelfRenderer(userMediaStream)
                        }
                    }
                    rawMembers.add(selfCoreMember!!)
                    chatServices.forEach { it.first.dispose() }
                    chatServices.clear()
                    configuration.messageConfigs.forEach { messageConfig ->
                        chatServices.add(
                            Pair(
                                RoomChatServiceFactory.createRoomChatService(
                                    service,
                                    messageConfig.batchSize,
                                    messageConfig.mimeTypes.toTypedArray()
                                ),
                                messageConfig.mimeTypes
                            )
                        )
                    }
                    if (!configuration.joinSilently) {
                        updateMember(
                            selfCoreMember!!.memberId,
                            configuration.memberRole,
                            MemberState.ACTIVE,
                            configuration.memberName
                        )
                        if (selfCoreMember?.isModerator == true) {
                            publishToRoom(
                                configuration.roomAlias,
                                MemberRole.MODERATOR,
                                configuration.memberName
                            )
                        }
                    }
                    observeChatServices()
                    observeMemberCount()
                    observeRoomMembers()
                    _onEvent.tryEmit(PhenixEvent.PHENIX_ROOM_JOINED)
                }
            }
        }
    }

    fun setVideoEnabled(memberId: String, enabled: Boolean) {
        Timber.d("Switching video streams: $enabled")
        rawMembers.find { it.memberId == memberId }?.run {
            isVideoEnabled = enabled
            if (isSelf) {
                if (enabled) {
                    publisher?.enableVideo()
                } else {
                    publisher?.disableVideo()
                }
            }
        }
    }

    fun setAudioEnabled(memberId: String, enabled: Boolean) {
        Timber.d("Switching audio streams: $enabled")
        rawMembers.find { it.memberId == memberId }?.run {
            isAudioEnabled = enabled
            if (isSelf) {
                if (enabled) {
                    publisher?.enableAudio()
                } else {
                    publisher?.disableAudio()
                }
            }
        }
    }

    fun setAudioLevel(memberId: String, level: Float) {
        Timber.d("Changing member audio level: $memberId, $level")
        rawMembers.find { it.memberId == memberId }?.audioLevel = if (level < 0f) 0f else if (level > 1f) 1f else level
    }

    fun stopPublishing() {
        Timber.d("Stopping media publishing")
        publisher?.stop()
    }

    fun updateMember(memberId: String, role: MemberRole?, state: MemberState?, name: String?) {
        rawMembers.find { it.memberId == memberId }?.let { member ->
            Timber.d("Updating member: $member with: ${role ?: member.memberRole}, ${state ?: member.memberState}, ${name ?: member.memberName}")
            member.updateMember(
                role ?: member.memberRole,
                state ?: member.memberState,
                name ?: member.memberName,
                onError = {
                    _onError.tryEmit(PhenixError.UPDATE_MEMBER_FAILED)
                }
            )
        }
    }

    fun sendMessage(message: String, mimeType: String) {
        chatServices.firstOrNull { it.second.contains(mimeType) }?.first?.sendMessageToRoom(message, mimeType) { status, _ ->
            Timber.d("Message: $message sent with status: $status")
        }
    }

    fun selectMember(memberId: String, isSelected: Boolean) {
        Timber.d("Selecting member: ${rawMembers.find { it.member.sessionId == memberId }}, $isSelected")
        rawMembers.find { it.member.sessionId == memberId }?.isSelected = isSelected
    }

    fun publishToRoom(alias: String, role: MemberRole, name: String?) {
        if (currentRoomType == null || userMediaStream == null) {
            _onError.tryEmit(PhenixError.PUBLISH_ROOM_FAILED)
            return
        }
        Timber.d("Publishing to room: $alias, $role, $name")
        val roomOptions = getRoomOptions(alias, currentRoomType!!)
        val publishOptions = getPublishOptions(userMediaStream!!)
        val publishToRoomOptions = getPublishToRoomOptions(roomOptions, publishOptions, role, name)
        roomExpress.publishToRoom(publishToRoomOptions) { publisher ->
            this.publisher = publisher
            if (publisher == null) {
                _onError.tryEmit(PhenixError.PUBLISH_ROOM_FAILED)
            }
        }
    }

    fun leaveRoom() {
        roomService?.leaveRoom { _, status ->
            Timber.d("Room left with status: $status")
            dispose()
            _onEvent.tryEmit(PhenixEvent.PHENIX_ROOM_LEFT)
        }
    }

    fun renderOnSurface(memberId: String, surfaceView: SurfaceView?) {
        rawMembers.find { it.memberId == memberId }?.renderOnSurface(surfaceView)
    }

    fun subscribeRoomMembers() {
        roomConfiguration?.joinSilently = false
        rawMembers.forEach { member ->
            member.subscribeToMemberMedia(roomExpress)
            launchIO { member.onUpdated.collect { _members.tryEmit(rawMembers.asPhenixMembers()) } }
            launchIO { member.onError.collect { _onError.tryEmit(it) } }
        }
    }

    private fun dispose() {
        rawMessages.clear()
        rawMembers.forEach { it.dispose() }
        rawMembers.clear()
        chatServices.forEach { it.first.dispose() }
        chatServices.clear()
        roomService?.dispose()
        roomService = null
        disposables.forEach { disposable ->
            disposable?.dispose()
        }
        disposables.clear()
    }

    private fun observeChatServices() {
        chatServices.forEach { service ->
            service.first.observableChatMessages?.subscribe { messages ->
                messages.lastOrNull()?.takeIf { it.observableTimeStamp.value.time > joinedDate.time }?.let { last ->
                    Timber.d("Phenix message received: ${last.observableMessage.value}")
                    rawMessages.add(last.asPhenixMessage())
                    _messages.tryEmit(rawMessages.asCopy())
                }
            }.run { disposables.add(this) }
        }
    }

    private fun observeMemberCount(){
        roomService?.observableActiveRoom?.value?.observableEstimatedSize?.subscribe { size ->
            _memberCount.tryEmit(size)
        }.run { disposables.add(this) }
    }

    private fun disposeGoneMembers(members: List<PhenixCoreMember>) {
        rawMembers.forEach { member ->
            members.find { it.isThisMember(member.member.sessionId) }?.takeIf { it.isDisposable }?.let {
                Timber.d("Disposing gone member: $it")
                it.dispose()
            }
        }
    }

    private fun observeRoomMembers() {
        if (roomService == null) {
            _onError.tryEmit(PhenixError.JOIN_ROOM_FAILED)
            return
        }
        roomService!!.observableActiveRoom.value.observableMembers.subscribe { members ->
            Timber.d("Received RAW members count: ${members.size}")
            val selfId = roomService!!.self.sessionId
            members.forEach { Timber.d("RAW Member: ${it.observableScreenName.value} ${it.sessionId == selfId}") }
            val memberList = mutableListOf(roomService!!.self.mapRoomMember(rawMembers, selfId))
            val mappedMembers = members.filterNot { it.sessionId == selfId }.mapTo(memberList) {
                it.mapRoomMember(rawMembers, selfId)
            }
            disposeGoneMembers(mappedMembers)
            rawMembers.clear()
            rawMembers.addAll(mappedMembers)
            if (roomConfiguration?.joinSilently == false) {
                subscribeRoomMembers()
            }
            _members.tryEmit(rawMembers.asPhenixMembers())
        }.run { disposables.add(this) }
    }

}
