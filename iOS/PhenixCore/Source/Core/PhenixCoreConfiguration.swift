//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore {
    struct Configuration {
        /// A URL indicating PCast endpoint for the PhenixSdk.
        @available(*, deprecated, message: "PCast uri must be encoded inside the `PhenixCore.Configuration.authToken`.")
        public let uri: URL?

        /// An authentification token for the PhenixSdk.
        public let authToken: String

        /// A log level for the PhenixSdk.
        /// Defaults to .``LogLevel-swift.enum/off``
        public let logLevel: LogLevel

        public init(authToken: String, logLevel: LogLevel = .off) {
            self.uri = nil
            self.logLevel = logLevel
            self.authToken = authToken
        }

        @available(*, deprecated, message: "Encode PCast uri inside the `authToken` and do not provide it explicitly.")
        public init(authToken: String, uri: URL, logLevel: LogLevel = .off) {
            self.uri = uri
            self.logLevel = logLevel
            self.authToken = authToken
        }
    }
}

extension PhenixCore.Configuration {
    public enum LogLevel: String {
        case all, trace, debug, info, warn, error, fatal, off
    }
}
