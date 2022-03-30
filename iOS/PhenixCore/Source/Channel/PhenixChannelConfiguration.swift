//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.Channel {
    struct Configuration: CustomStringConvertible {
        public let alias: String
        public let streamToken: String?
        public let publishToken: String?
        public let capabilities: [String]
        public let videoAspectRatio: PhenixCore.AspectRatio

        public var description: String {
            """
            Channel.Configuration(\
            alias: \(alias), \
            capabilities: \(capabilities), \
            streamToken: \(String(describing: streamToken)), \
            publishToken: \(String(describing: publishToken))\
            )
            """
        }

        public init(
            alias: String,
            streamToken: String?,
            publishToken: String?,
            capabilities: [String],
            videoAspectRatio: PhenixCore.AspectRatio = .fit
        ) {
            self.alias = alias
            self.streamToken = streamToken
            self.publishToken = publishToken
            self.capabilities = capabilities
            self.videoAspectRatio = videoAspectRatio
        }

        public init(alias: String, publishToken: String) {
            self.init(alias: alias, streamToken: nil, publishToken: publishToken, capabilities: [])
        }

        public init(alias: String, publishToken: String, capabilities: [String]) {
            self.init(alias: alias, streamToken: nil, publishToken: publishToken, capabilities: [])
        }

        public init(alias: String, streamToken: String, videoAspectRatio: PhenixCore.AspectRatio = .fit) {
            self.init(alias: alias, streamToken: streamToken, publishToken: nil, capabilities: [], videoAspectRatio: videoAspectRatio)
        }
    }
}
