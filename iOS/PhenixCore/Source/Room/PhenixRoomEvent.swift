//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Room {
    enum Event {
        case roomCreating(alias: String)
        case roomCreationFailed(alias: String, error: Error)
        case roomCreated(alias: String)
        case roomJoining(alias: String)
        case roomJoiningFailed(alias: String, error: Error)
        case roomJoined(alias: String)
        case roomPublishing(alias: String)
        case roomPublishingFailed(alias: String, error: Error)
        case roomPublished(alias: String)
        case roomLeaving(alias: String)
        case roomLeavingFailed(alias: String, error: Error)
        case roomLeft(alias: String)
        case roomMemberSubscriptionFailed(roomAlias: String, error: Error)
        case roomMemberMediaChangeFailed(memberID: PhenixCore.Member.ID, error: Error)
    }
}
