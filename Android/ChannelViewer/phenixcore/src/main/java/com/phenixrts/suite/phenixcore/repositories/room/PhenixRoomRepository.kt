/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.room

import android.view.SurfaceView
import android.widget.ImageView
import com.phenixrts.common.Disposable
import com.phenixrts.common.RequestStatus
import com.phenixrts.express.*
import com.phenixrts.pcast.UserMediaStream
import com.phenixrts.room.*
import com.phenixrts.suite.phenixcore.BuildConfig
import com.phenixrts.suite.phenixcore.common.*
import com.phenixrts.suite.phenixcore.repositories.chat.PhenixChatRepository
import com.phenixrts.suite.phenixcore.repositories.core.common.*
import com.phenixrts.suite.phenixcore.repositories.core.common.getPublishOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.getPublishToRoomOptions
import com.phenixrts.suite.phenixcore.repositories.core.common.getRoomOptions
import com.phenixrts.suite.phenixcore.repositories.models.*
import com.phenixrts.suite.phenixcore.repositories.room.models.PhenixCoreMember
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

internal class PhenixRoomRepository(
    private val roomExpress: RoomExpress,
    private val configuration: PhenixConfiguration,
    private val chatRepository: PhenixChatRepository
) {

    private val rawMembers = mutableSetOf<PhenixCoreMember>()
    private val observableRoomDisposables: MutableSet<Disposable?> = mutableSetOf()
    private var roomDisposable: Disposable? = null
    private var rawRoom: PhenixRoom? = null
    private var roomConfiguration: PhenixRoomConfiguration? = null

    private val _onError = ConsumableSharedFlow<PhenixError>()
    private val _onEvent = ConsumableSharedFlow<PhenixEvent>()
    private val _members = ConsumableSharedFlow<List<PhenixMember>>(canReplay = true)
    private val _room = ConsumableSharedFlow<PhenixRoom?>(canReplay = true)
    private var _memberCount = ConsumableSharedFlow<Long>(canReplay = true)

    private var publisher: ExpressPublisher? = null
    private var joinRoomService: RoomService? = null
    private var publishRoomService: RoomService? = null

    private val selfCoreMember get() = rawMembers.find { it.isSelf }

    val members = _members.asSharedFlow()
    val room = _room.asSharedFlow()
    val onError = _onError.asSharedFlow()
    val onEvent = _onEvent.asSharedFlow()
    val memberCount = _memberCount.asSharedFlow()
    val isRoomJoined get() = joinRoomService != null

    fun joinRoom(configuration: PhenixRoomConfiguration) {
        _onEvent.tryEmit(PhenixEvent.PHENIX_ROOM_JOINING.apply { data = roomConfiguration })
        Timber.d("Joining room with configuration: $configuration")
        roomExpress.joinRoom(configuration) { service ->
            joinRoomService = service
            if (joinRoomService == null) {
                _onError.tryEmit(PhenixError.JOIN_ROOM_FAILED.apply { data = roomConfiguration })
            } else {
                onRoomJoined(configuration, PhenixEvent.PHENIX_ROOM_JOINED)
            }
        }
    }

    fun createRoom(configuration: PhenixRoomConfiguration) {
        roomConfiguration = configuration
        _onEvent.tryEmit(PhenixEvent.PHENIX_ROOM_CREATING.apply { data = roomConfiguration })
        Timber.d("Creating room with configuration: $configuration")
        roomExpress.createRoom(configuration) { status ->
            if (status == RequestStatus.OK) {
                _onEvent.tryEmit(PhenixEvent.PHENIX_ROOM_CREATED.apply { data = roomConfiguration })
            } else {
                _onError.tryEmit(PhenixError.CREATE_ROOM_FAILED.apply { data = roomConfiguration })
            }
        }
    }

    fun publishToRoom(roomConfiguration: PhenixRoomConfiguration, userMediaStream: UserMediaStream) {
        Timber.d("Publishing to room: $roomConfiguration")
        _onEvent.tryEmit(PhenixEvent.PHENIX_ROOM_PUBLISHING.apply { data = roomConfiguration })
        val roomOptions = getRoomOptions(roomConfiguration)
        val publishOptions = getPublishOptions(
            userMediaStream,
            configuration.publishToken,
            emptyList()
        )
        val publishToRoomOptions = getPublishToRoomOptions(roomOptions, publishOptions, roomConfiguration)
        roomExpress.publishInRoom(publishToRoomOptions) { publisher, service ->
            this.publisher = publisher
            this.publishRoomService = service
            if (publisher == null || service == null) {
                _onError.tryEmit(PhenixError.PUBLISH_ROOM_FAILED.apply { data = roomConfiguration })
            } else {
                onRoomJoined(roomConfiguration, PhenixEvent.PHENIX_ROOM_PUBLISHED)
            }
        }
    }

    fun stopPublishingToRoom() {
        Timber.d("Stopping media publishing")
        publisher?.stop()
        publisher?.dispose()
        publisher = null
    }

    fun setVideoEnabled(memberId: String, enabled: Boolean) {
        findMember(memberId)?.enableVideo(enabled)
    }

    fun setSelfVideoEnabled(enabled: Boolean) {
        selfCoreMember?.enableVideo(enabled)
    }

    fun setAudioEnabled(memberId: String, enabled: Boolean) {
        findMember(memberId)?.enableAudio(enabled)
    }

    fun setSelfAudioEnabled(enabled: Boolean) {
        selfCoreMember?.enableAudio(enabled)
    }

    fun setAudioLevel(memberId: String, level: Float) {
        Timber.d("Changing member audio level: $memberId, $level")
        findMember(memberId)?.audioLevel = if (level < 0f) 0f else if (level > 1f) 1f else level
    }

    fun updateMember(memberId: String, role: PhenixMemberRole?, state: PhenixMemberState?, name: String?) {
        findMember(memberId)?.let { member ->
            Timber.d("Updating member: $member with: ${role ?: member.memberRole}, ${state ?: member.memberState}, ${name ?: member.memberName}")
            member.updateMember(
                role?.asMemberRole() ?: member.memberRole,
                state?.asMemberState() ?: member.memberState,
                name ?: member.memberName,
                onUpdated = {
                    _onEvent.tryEmit(PhenixEvent.PHENIX_MEMBER_UPDATED)
                },
                onError = {
                    _onError.tryEmit(PhenixError.UPDATE_MEMBER_FAILED)
                }
            )
        }
    }

    fun selectMember(memberId: String, isSelected: Boolean) {
        Timber.d("Selecting member: $memberId, $isSelected")
        findMember(memberId)?.isSelected = isSelected
    }

    fun renderOnSurface(memberId: String, surfaceView: SurfaceView?) {
        Timber.d("Render on surface called")
        findMember(memberId)?.renderOnSurface(surfaceView)
    }

    fun renderOnImage(memberId: String, imageView: ImageView?, configuration: PhenixFrameReadyConfiguration?) {
        Timber.d("Render on image called")
        findMember(memberId)?.renderOnImage(imageView, configuration)
    }

    fun subscribeRoomMembers() = synchronized(rawMembers) {
        roomConfiguration?.joinSilently = false
        var videoRenderers = 0
        rawMembers.forEach { member ->
            val canRenderVideo = videoRenderers <= (roomConfiguration?.maxVideoRenderers ?: BuildConfig.MAX_VIDEO_RENDERERS)
            member.subscribeToMemberMedia(canRenderVideo)
            if (member.isVideoRendering) videoRenderers++
            launchIO { member.onError.collect { _onError.tryEmit(it) } }
            launchIO {
                member.onUpdated.collect {
                    synchronized(rawMembers) {
                        _members.tryEmit(rawMembers.asPhenixMembers())
                    }
                }
            }
        }
    }

    fun subscribeForMessages(alias: String, configuration: PhenixMessageConfiguration) {
        val roomService = joinRoomService
        if (roomConfiguration?.roomAlias != alias || roomService == null) return
        chatRepository.subscribeForMessages(alias, roomService, configuration)
    }

    fun leaveRoom() {
        stopPublishingToRoom()
        roomConfiguration?.roomAlias?.let { alias ->
            chatRepository.disposeChatService(alias)
        }
        joinRoomService?.leaveRoom { _, status ->
            Timber.d("Room left with status: $status")
            release()
            _room.tryEmit(null)
            _onEvent.tryEmit(PhenixEvent.PHENIX_ROOM_LEFT.apply { data = roomConfiguration })
        }
    }

    fun release() {
        roomConfiguration = null
        rawRoom = null

        rawMembers.forEach { it.dispose() }
        rawMembers.clear()
        joinRoomService?.dispose()
        joinRoomService = null
        publishRoomService?.dispose()
        publishRoomService = null
        publisher?.dispose()
        publisher = null
        observableRoomDisposables.forEach { disposable ->
            disposable?.dispose()
        }
        observableRoomDisposables.clear()
        roomDisposable?.dispose()
        roomDisposable = null
        _members.tryEmit(emptyList())
        _memberCount.tryEmit(0)
    }

    private fun findMember(memberId: String) = rawMembers.find { it.memberId == memberId }

    private fun onRoomJoined(configuration: PhenixRoomConfiguration, event: PhenixEvent) {
        val roomService = joinRoomService
        val self = joinRoomService?.self
        if (roomService == null || self == null) return
        roomConfiguration = configuration
        Timber.d("Room joined with configuration: $configuration")
        val selfCoreMember = PhenixCoreMember(self, true, roomExpress, configuration)
        selfCoreMember.enableAudio(configuration.audioEnabled)
        selfCoreMember.enableVideo(configuration.videoEnabled)
        rawMembers.add(selfCoreMember)

        if (!configuration.joinSilently) {
            updateMember(
                selfCoreMember.memberId,
                configuration.memberRole,
                PhenixMemberState.ACTIVE,
                configuration.memberName
            )
        }
        roomService.observableActiveRoom?.subscribe { room ->
            Timber.d("Observable active room collected: $room")
            if (isJoinedRoomInvalid() || room == null) {
                roomDisposable?.dispose()
                roomDisposable = null
                return@subscribe
            }
            observableRoomDisposables.forEach { it?.dispose() }
            observableRoomDisposables.clear()
            observeRoomStatus()
            observeMemberCount()
            observeRoomMembers()
            rawRoom = PhenixRoom(id = room.roomId, alias = room.observableAlias.value)
            _room.tryEmit(rawRoom?.copy())
            _onEvent.tryEmit(event.apply { data = configuration })
        }?.run {
            roomDisposable?.dispose()
            roomDisposable = this
        }
    }

    private fun observeRoomStatus() {
        roomExpress.pCastExpress.observableIsOnlineStatus.subscribe { isOnline ->
            if (!isOnline) {
                Timber.d("Online state changed: $isOnline")
                _onError.tryEmit(PhenixError.ROOM_GONE.apply { data = roomConfiguration })
            }
        }.run { observableRoomDisposables.add(this) }
    }

    private fun observeMemberCount(){
        joinRoomService?.observableActiveRoom?.value?.observableEstimatedSize?.subscribe { size ->
            _memberCount.tryEmit(size)
        }.run { observableRoomDisposables.add(this) }
    }

    private fun disposeGoneMembers(members: Set<PhenixCoreMember>) = synchronized(rawMembers) {
        val rawMembersMap = rawMembers.associateBy { it.memberId }
        members.filter { member -> rawMembersMap[member.memberId]?.isDisposable == true }.forEach { member ->
            Timber.d("Disposing gone member: $member")
            member.dispose()
        }
    }

    private fun observeRoomMembers() = synchronized(rawMembers) {
        val roomService = joinRoomService ?: return@synchronized
        roomService.observableActiveRoom?.value?.observableMembers?.subscribe { members ->
            roomService.self?.let { self ->
                Timber.d("Received RAW members count: ${members.size}")
                val selfId = self.sessionId
                members.forEach { Timber.d("RAW Member: ${it.observableScreenName.value} ${it.sessionId == selfId}") }
                val memberList = mutableSetOf(
                    self.mapRoomMember(rawMembers, selfId, roomExpress, roomConfiguration)
                )
                val mappedMembers = members.filterNot { it.sessionId == selfId }.mapTo(memberList) {
                    it.mapRoomMember(rawMembers, selfId, roomExpress, roomConfiguration)
                }
                disposeGoneMembers(mappedMembers)
                rawMembers.clear()
                rawMembers.addAll(mappedMembers)
                if (roomConfiguration?.joinSilently == false) {
                    subscribeRoomMembers()
                }
                _members.tryEmit(rawMembers.asPhenixMembers())
            }
        }.run { observableRoomDisposables.add(this) }
    }

    private fun isJoinedRoomInvalid(): Boolean {
        if (joinRoomService == null || joinRoomService?.self == null) {
            _onError.tryEmit(PhenixError.JOIN_ROOM_FAILED.apply { data = roomConfiguration })
            return true
        }
        return false
    }

    private fun PhenixCoreMember.enableAudio(enabled: Boolean) {
        Timber.d("Switching audio to: $enabled for: ${toString()}")
        isAudioEnabled = enabled
        if (isSelf) {
            if (enabled) {
                publisher?.enableAudio()
                _onEvent.tryEmit(PhenixEvent.AUDIO_ENABLED)
            } else {
                publisher?.disableAudio()
                _onEvent.tryEmit(PhenixEvent.AUDIO_DISABLED)
            }
        }
    }

    private fun PhenixCoreMember.enableVideo(enabled: Boolean) {
        Timber.d("Switching video to: $enabled for: ${toString()}")
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
