//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk
import UIKit

public enum PhenixConfiguration {
    public static var edgeToken: String?

    public static func makeChannelExpress() -> PhenixChannelExpress {
        precondition((edgeToken != nil), "You must provide the EdgeToken.")

        var pcastExpressOptionsBuilder = PhenixPCastExpressFactory.createPCastExpressOptionsBuilder { status, description in
            DispatchQueue.main.async {
                AppDelegate.terminate(
                  afterDisplayingAlertWithTitle: "Phenix SDK returned an unrecoverable error with status [\(status)] and description [\(String(describing: description))].",
                    message: "Application will be terminated."
                )
            }
        }

        if let edgeToken = edgeToken {
            pcastExpressOptionsBuilder = pcastExpressOptionsBuilder?.withAuthenticationToken(edgeToken)
        }

        let pcastExpressOptions = pcastExpressOptionsBuilder?
            .withMinimumConsoleLogLevel("Info")
            .buildPCastExpressOptions()

        let roomExpressOptions = PhenixRoomExpressFactory.createRoomExpressOptionsBuilder()
            .withPCastExpressOptions(pcastExpressOptions)
            .buildRoomExpressOptions()

        let channelExpressOptions = PhenixChannelExpressFactory.createChannelExpressOptionsBuilder()
            .withRoomExpressOptions(roomExpressOptions)
            .buildChannelExpressOptions()

        return PhenixChannelExpressFactory.createChannelExpress(channelExpressOptions)
    }

    public static func makeJoinChannelOptions(with videoLayer: CALayer?) -> PhenixJoinChannelOptions! {
        let rendererOptions = PhenixRendererOptions()
        rendererOptions.aspectRatioMode = .letterbox

        if videoLayer == nil {
            rendererOptions.preferredVideoRenderDeviceType = PhenixVideoRenderDeviceType.typeAutomatic
            rendererOptions.useNullVideoDevice = true
        }

        var joinChannelOptionsBuilder = PhenixChannelExpressFactory.createJoinChannelOptionsBuilder()
            .withRendererOptions(rendererOptions)

        if let edgeToken = edgeToken {
            joinChannelOptionsBuilder = joinChannelOptionsBuilder?
                .withStreamToken(edgeToken)
        }

        if let videoLayer = videoLayer {
            joinChannelOptionsBuilder = joinChannelOptionsBuilder?
                .withRenderer(videoLayer)
        }

        return joinChannelOptionsBuilder?.buildJoinChannelOptions()
    }
}
