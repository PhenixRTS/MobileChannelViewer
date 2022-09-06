/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.chat

import com.phenixrts.chat.RoomChatServiceFactory
import com.phenixrts.common.RequestStatus
import com.phenixrts.room.RoomService
import com.phenixrts.suite.phenixcore.common.ConsumableSharedFlow
import com.phenixrts.suite.phenixcore.common.asCopy
import com.phenixrts.suite.phenixcore.common.asPhenixMessage
import com.phenixrts.suite.phenixcore.repositories.chat.models.PhenixChatService
import com.phenixrts.suite.phenixcore.repositories.models.PhenixError
import com.phenixrts.suite.phenixcore.repositories.models.PhenixEvent
import com.phenixrts.suite.phenixcore.repositories.models.PhenixMessage
import com.phenixrts.suite.phenixcore.repositories.models.PhenixMessageConfiguration
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

internal class PhenixChatRepository {

    private val rawMessages = mutableSetOf<PhenixMessage>()
    private val _onError = ConsumableSharedFlow<PhenixError>()
    private val _onEvent = ConsumableSharedFlow<PhenixEvent>()
    private val _messages = ConsumableSharedFlow<List<PhenixMessage>>(canReplay = true)

    val onError = _onError.asSharedFlow()
    val onEvent = _onEvent.asSharedFlow()
    val messages = _messages.asSharedFlow()

    private val chatServices = mutableSetOf<PhenixChatService>()

    fun sendMessage(alias: String, message: String, mimeType: String) {
        Timber.d("Sending message: $message with: $mimeType to: $alias")
        chatServices.findChatService(alias, mimeType)?.let { chatService ->
            if (mimeType.isNotBlank()) {
                chatService.service.sendMessageToRoom(message, mimeType) { status, _ ->
                    onMessageSent(message, status)
                }
            } else {
                chatService.service.sendMessageToRoom(message) { status, _ ->
                    onMessageSent(message, status)
                }
            }
        } ?: _onError.tryEmit(PhenixError.MISSING_CHAT_SERVICE)
    }

    fun disposeChatService(alias: String) {
        val disposedServices = mutableSetOf<PhenixChatService>()
        chatServices.filter { it.alias == alias }.forEach { chatService ->
            chatService.disposable?.dispose()
            chatService.service.dispose()
            disposedServices.add(chatService)
        }
        chatServices.removeAll(disposedServices)
    }

    fun subscribeForMessages(alias: String, service: RoomService, configuration: PhenixMessageConfiguration) {
        chatServices.addIfNew(alias, configuration, service)
        chatServices.findChatService(alias, configuration.mimeType)?.let { chatService ->
            Timber.d("Observing chat with mimetype: ${chatService.mimeType} in: ${chatService.alias}")
            chatService.disposable?.dispose()
            chatService.service.observableLastChatMessage?.subscribe { message ->
                message?.takeIf { it.observableTimeStamp.value.time > configuration.joinedDate }?.let { last ->
                    Timber.d("Phenix message received: ${last.observableMessage.value}")
                    rawMessages.add(last.asPhenixMessage(alias))
                    _messages.tryEmit(rawMessages.asCopy())
                }
            }.run {
                chatService.disposable = this
            }
        }
    }

    fun release() {
        chatServices.forEach {
            it.service.dispose()
            it.disposable?.dispose()
        }
        chatServices.clear()
        rawMessages.clear()
    }

    private fun onMessageSent(message: String, status: RequestStatus) {
        Timber.d("Message: $message sent with status: $status")
        if (status == RequestStatus.OK) {
            _onEvent.tryEmit(PhenixEvent.MESSAGE_SENT)
        } else {
            _onError.tryEmit(PhenixError.SEND_MESSAGE_FAILED)
        }
    }

    private fun MutableSet<PhenixChatService>.findChatService(alias: String, mimeType: String? = null) =
        firstOrNull { if (mimeType != null) it.alias == alias && it.mimeType == mimeType else it.alias == alias }

    private fun MutableSet<PhenixChatService>.addIfNew(
        alias: String,
        configuration: PhenixMessageConfiguration,
        service: RoomService
    ) {
        if (none { it.alias == alias && it.mimeType == configuration.mimeType }) {
            val chatService = if (configuration.mimeType.isNotBlank()) {
                RoomChatServiceFactory.createRoomChatService(
                    service,
                    configuration.batchSize,
                    listOf(configuration.mimeType).toTypedArray()
                )
            } else {
                RoomChatServiceFactory.createRoomChatService(service)
            }
            add(PhenixChatService(
                alias,
                configuration.mimeType,
                chatService
            ))
        }
    }
}
