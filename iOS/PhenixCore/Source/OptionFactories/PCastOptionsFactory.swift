//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

enum PCastOptionsFactory {
    typealias UnrecoverableErrorHandler = (_ description: String?) -> Void

    private static let logger = OSLog(identifier: "PCastOptionsFactory")

    static func makePCastExpressOptions(
        configuration: PhenixCore.Configuration,
        unrecoverableErrorCallback: @escaping UnrecoverableErrorHandler
    ) -> PhenixPCastExpressOptions {
        let pcastExpressOptionsBuilder: PhenixPCastExpressOptionsBuilder = PhenixPCastExpressFactory
            .createPCastExpressOptionsBuilder()
            .withAuthenticationToken(configuration.authToken)
            .withMinimumConsoleLogLevel(configuration.logLevel.rawValue)
            .withUnrecoverableErrorCallback { _, description in
                os_log(.error, log: Self.logger, "Unrecoverable error: %{private}s", String(describing: description))
                unrecoverableErrorCallback(description)
            }

        if let pcast = configuration.uri {
            pcastExpressOptionsBuilder.withPCastUri(pcast.absoluteString)
        }

        return pcastExpressOptionsBuilder.buildPCastExpressOptions()
    }

    static func makeSubscriberOptions(configuration: PhenixCore.Stream.Configuration) -> PhenixSubscribeOptions {
        let builder: PhenixSubscribeOptionsBuilder = PhenixPCastExpressFactory.createSubscribeOptionsBuilder()

        if let token = configuration.streamToken {
            builder.withStreamToken(token)
        } else {
            builder.withCapabilities(["on-demand"])
        }

        builder.withStreamId(configuration.id)

        return builder.buildSubscribeOptions()
    }
}
