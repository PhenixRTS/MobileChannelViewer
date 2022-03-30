//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

protocol MemberSubscriptionInformationProvider: AnyObject {
    /// Checks if the limit of the member subscription with video is reached.
    /// - Returns: Bool, `true` - can subscribe with video, `false` - limit is reached, should not subscribe with video
    func canMemberSubscribeForVideo() -> Bool
}
