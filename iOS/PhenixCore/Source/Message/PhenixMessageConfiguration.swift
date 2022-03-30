//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Message {
    struct Configuration: CustomStringConvertible {
        public var batchSize: UInt
        public var mimeType: String

        public var description: String {
            "Message.Configuration(batchSize: \(batchSize), mimeType: \(mimeType))"
        }

        public init(batchSize: UInt, mimeType: String) {
            self.batchSize = batchSize
            self.mimeType = mimeType
        }
    }
}

public extension PhenixCore.Message.Configuration {
    struct MimeType: RawRepresentable, Hashable {
        public var rawValue: String

        public init?(rawValue: String) {
            self.rawValue = rawValue
        }
    }
}
