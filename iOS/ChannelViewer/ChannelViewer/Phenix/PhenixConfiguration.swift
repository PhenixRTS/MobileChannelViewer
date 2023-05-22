//
//  Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk
import UIKit

public enum PhenixConfiguration {
    public static var pcastUri: URL?
    public static var backendUri: URL? = URL(string: "https://demo.phenixrts.com/pcast")
    public static var edgeToken: String?
    public static let capabilities: [String] = ["real-time"]
    public static var channelAlias: String?

    public static func makeChannelExpress() -> PhenixChannelExpress {
        precondition((backendUri != nil) != (edgeToken != nil), "You must provide the EdgeToken or the backend url. At least one must be provided but not both simultaneously.")

        var pcastExpressOptionsBuilder = PhenixPCastExpressFactory.createPCastExpressOptionsBuilder()

        if let edgeToken = edgeToken {
            pcastExpressOptionsBuilder = pcastExpressOptionsBuilder?.withAuthenticationToken(edgeToken)
        } else if let backendUri = backendUri {
            pcastExpressOptionsBuilder = pcastExpressOptionsBuilder?.withBackendUri(backendUri.absoluteString)
        }

        if let pcastUri = pcastUri {
            pcastExpressOptionsBuilder = pcastExpressOptionsBuilder?.withPCastUri(pcastUri.absoluteString)
        }

        let pcastExpressOptions = pcastExpressOptionsBuilder?
            .withMinimumConsoleLogLevel("Info")
            .withUnrecoverableErrorCallback { status, description in
                DispatchQueue.main.async {
                    AppDelegate.terminate(
                        afterDisplayingAlertWithTitle: "Something went wrong!",
                        message: "Application entered in unrecoverable state and will be terminated."
                    )
                }
            }
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
        var joinRoomOptionsBuilder = PhenixRoomExpressFactory.createJoinRoomOptionsBuilder()

        if edgeToken == nil {
            joinRoomOptionsBuilder = joinRoomOptionsBuilder?.withCapabilities(capabilities)
        }

        if let channelAlias = channelAlias {
            joinRoomOptionsBuilder = joinRoomOptionsBuilder?
                .withRoomAlias(channelAlias)
        }

        let joinRoomOptions = joinRoomOptionsBuilder?.buildJoinRoomOptions()

        let rendererOptions = PhenixRendererOptions()
        rendererOptions.aspectRatioMode = .letterbox

        if videoLayer == nil {
            rendererOptions.preferredVideoRenderDeviceType = PhenixVideoRenderDeviceType.typeAutomatic
            rendererOptions.useNullVideoDevice = true
        }

        var joinChannelOptionsBuilder = PhenixChannelExpressFactory.createJoinChannelOptionsBuilder()

        if let edgeToken = edgeToken {
            joinChannelOptionsBuilder = joinChannelOptionsBuilder?
                .withStreamToken(edgeToken)
                .withSkipRetryOnUnauthorized()
        }

        joinChannelOptionsBuilder = joinChannelOptionsBuilder?
            .withJoinRoomOptions(joinRoomOptions)
            .withRendererOptions(rendererOptions)

        if let videoLayer = videoLayer {
            joinChannelOptionsBuilder = joinChannelOptionsBuilder?
                .withRenderer(videoLayer)
        }

        return joinChannelOptionsBuilder?.buildJoinChannelOptions()
    }
}
