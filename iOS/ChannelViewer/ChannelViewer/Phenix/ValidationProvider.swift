//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public enum ValidationProvider {
    public static func validate(edgeToken: String?) throws {
        switch edgeToken {
        case .some:
            break
        case .none:
            throw Error(reason: "EdgeToken must be provided.")
        }
    }
}

extension ValidationProvider {
    public struct Error: Swift.Error, LocalizedError {
        public let reason: String
        public var errorDescription: String? { reason }
    }
}
