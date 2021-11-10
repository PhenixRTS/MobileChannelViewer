/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

enum class PhenixError(val message: String) {
    // Phenix
    FAILED_TO_INITIALIZE("The PhenixCore failed to initialize. Check your network status and configuration."),
    NOT_INITIALIZED("The PhenixCore is not initialized. Please call .init() before using any other function."),
    ALREADY_INITIALIZING("The PhenixCore is already initializing."),
    ALREADY_INITIALIZED("The PhenixCore is already initialized."),

    // General
    FAILED_TO_COLLECT_LOGS("Failed to collect logs"),

    // Channel
    CREATE_RENDERER_FAILED("Failed to create video renderer, check your configuration."),
    RENDERING_FAILED("Failed to render video, check your configuration."),
    CHANNEL_LIST_EMPTY("Failed to join channels from configuration, the list is empty."),

    // Room
    JOIN_ROOM_FAILED("Failed to join room with given id."),
    LEAVE_ROOM_FAILED("Failed to leave room."),
    PUBLISH_ROOM_FAILED("Failed to publish media to room."),
    UPDATE_MEMBER_FAILED("Failed to update member."),
    SEND_MESSAGE_FAILED("Failed to send message."),
}
