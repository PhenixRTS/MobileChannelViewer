//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

protocol MemberSubscriptionControllerDelegate: AnyObject {
    func subscriptionController(
        _ controller: PhenixCore.MemberSubscriptionController,
        didReceiveDataFrom stream: PhenixStream
    )

    func subscriptionController(
        _ controller: PhenixCore.MemberSubscriptionController,
        didReceiveDataFrom stream: PhenixStream,
        with subscriptions: Set<MemberSubscriptionService.Subscription>
    )

    func shouldSubscriptionControllerSubscribeVideoStream(
        _ controller: PhenixCore.MemberSubscriptionController
    ) -> Bool
}
