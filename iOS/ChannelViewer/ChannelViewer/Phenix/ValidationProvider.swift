//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public enum ValidationProvider {
    public static func validate(backend: URL?, edgeToken: String?, channelAlias: String?) throws {
        switch (backend, edgeToken, channelAlias) {
        case (.some(_), .some(_), _):
            throw Error(
                reason: "Provide only `backend` or `edgeToken`, backend url cannot be provided together with the token."
            )

        case (.some(_), _, .none):
            throw Error(reason: "Channel Alias must be provided.")

        default:
            // Do nothing, because this is a valid scenario
            break
        }
    }
}

extension ValidationProvider {
    public struct Error: Swift.Error, LocalizedError {
        public let reason: String
        public var errorDescription: String? { reason }
    }
}
