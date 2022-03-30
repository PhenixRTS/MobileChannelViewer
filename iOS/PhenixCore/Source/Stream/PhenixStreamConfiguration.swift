//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Stream {
    struct Configuration: CustomStringConvertible {
        public let id: String
        public let streamToken: String?

        public var description: String {
            """
            Channel.Configuration(\
            id: \(id), \
            streamToken: \(String(describing: streamToken))\
            )
            """
        }

        public init(id: String, streamToken: String?) {
            self.id = id
            self.streamToken = streamToken
        }
    }
}
