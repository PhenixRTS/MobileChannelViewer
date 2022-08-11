/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

enum class PhenixEvent(var data: Any? = null) {
    // Phenix
    PHENIX_CORE_INITIALIZED,

    // Channel
    /**
     * Will hold [PhenixChannelConfiguration] as data
     */
    PHENIX_CHANNEL_PUBLISHING,
    /**
     * Will hold [PhenixChannelConfiguration] as data
     */
    PHENIX_CHANNEL_PUBLISHED,
    /**
     * Will hold [PhenixChannelConfiguration] as data
     */
    PHENIX_CHANNEL_PUBLISH_ENDED,

    // Room
    /**
     * Will hold [PhenixRoomConfiguration] as data
     */
    PHENIX_ROOM_JOINING,
    /**
     * Will hold [PhenixRoomConfiguration] as data
     */
    PHENIX_ROOM_JOINED,
    /**
     * Will hold [PhenixRoomConfiguration] as data
     */
    PHENIX_ROOM_CREATING,
    /**
     * Will hold [PhenixRoomConfiguration] as data
     */
    PHENIX_ROOM_CREATED,
    /**
     * Will hold [PhenixRoomConfiguration] as data
     */
    PHENIX_ROOM_PUBLISHING,
    /**
     * Will hold [PhenixRoomConfiguration] as data
     */
    PHENIX_ROOM_PUBLISHED,
    /**
     * Will hold [PhenixRoomConfiguration] as data
     */
    PHENIX_ROOM_LEFT,

    // Member
    PHENIX_MEMBER_UPDATED,

    // User media
    CAMERA_FLIPPED,
    VIDEO_DISABLED,
    VIDEO_ENABLED,
    AUDIO_DISABLED,
    AUDIO_ENABLED,

    // General
    MESSAGE_SENT,
}
