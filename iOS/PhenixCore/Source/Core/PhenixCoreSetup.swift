//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

struct PhenixCoreSetup {
    typealias UnrecoverableErrorHandler = PCastOptionsFactory.UnrecoverableErrorHandler

    let configuration: PhenixCore.Configuration
    let unrecoverableErrorCallback: UnrecoverableErrorHandler

    func setupChannelExpress() -> PhenixChannelExpress {
        let pcastExpressOptions = PCastOptionsFactory.makePCastExpressOptions(
            configuration: configuration,
            unrecoverableErrorCallback: unrecoverableErrorCallback
        )
        let roomExpressOptions = RoomOptionsFactory.makeRoomExpressOptions(pcastExpressOptions: pcastExpressOptions)
        let channelExpressOptions = ChannelOptionsFactory.makeChannelExpressOptions(
            roomExpressOptions: roomExpressOptions
        )

        return PhenixChannelExpressFactory.createChannelExpress(channelExpressOptions)
    }
}
