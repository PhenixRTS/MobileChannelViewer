//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

enum PublishOptionsFactory {
    static func makePublishOptions(
        userMediaStream: PhenixUserMediaStream,
        publishToken: String?,
        capabilities: [String] = []
    ) -> PhenixPublishOptions {
        let builder: PhenixPublishOptionsBuilder = PhenixPCastExpressFactory.createPublishOptionsBuilder()

        builder.withUserMedia(userMediaStream)

        if let publishToken = publishToken {
            builder
                .withStreamToken(publishToken)
                .withSkipRetryOnUnauthorized()
        } else {
            // In cases, when the publish token is provided,
            // all capabilities are provided by the token,
            // so there is no need to provide a custom set of
            // capabilities - they would be automatically ignored.
            builder.withCapabilities(capabilities)
        }

        return builder.buildPublishOptions()
    }
}
