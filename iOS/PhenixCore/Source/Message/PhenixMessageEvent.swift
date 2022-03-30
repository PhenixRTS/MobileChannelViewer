//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Message {
    enum Event {
        case messageSubscriptionFailed
        case messageSubscriptionSucceeded
        case messageSubscriptionNotFound
    }
}
