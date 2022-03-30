//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Channel {
    enum Event {
        case channelCreating(alias: String)
        case channelCreationFailed(alias: String, error: Error)
        case channelCreated(alias: String)
        case channelJoining(alias: String)
        case channelJoiningFailed(alias: String, error: Error)
        case channelJoined(alias: String)
        case channelPublishing(alias: String)
        case channelPublishingFailed(alias: String, error: Error)
        case channelPublished(alias: String)
    }
}
