//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

enum StreamOptionsFactory {
    static func makeSubscribeToMemberStreamOptions(
        streamToken: String,
        streamEndHandler: ((PhenixStreamEndedReason) -> Void)? = nil
    ) -> PhenixSubscribeToMemberStreamOptions {
        let options = PhenixPCastExpressFactory.createMonitorOptionsBuilder().buildMonitorOptions()
        return PhenixRoomExpressFactory.createSubscribeToMemberStreamOptionsBuilder()
            .withStreamToken(streamToken)
            .withMonitor(nil, { reason, _, _ in streamEndHandler?(reason) }, options)
            .buildSubscribeToMemberStreamOptions()
    }
}
