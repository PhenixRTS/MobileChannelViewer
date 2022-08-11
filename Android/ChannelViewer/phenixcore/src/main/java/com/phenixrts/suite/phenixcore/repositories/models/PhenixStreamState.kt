/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.repositories.models

enum class PhenixStreamState {
    /**
     * Stream is offline
     */
    OFFLINE,

    /**
     * Stream is being joined
     */
    JOINING,

    /**
     * Stream is joined but it has no active stream playing
     */
    NO_STREAM,

    /**
     * Stream is joined and stream is playing
     */
    STREAMING
}
