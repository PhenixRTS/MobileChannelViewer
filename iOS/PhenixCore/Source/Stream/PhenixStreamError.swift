//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.Stream {
    struct Error: LocalizedError {
        public var errorDescription: String?
        public var failureReason: String?
        public var recoverySuggestion: String?

        public static func invalid(_ status: PhenixRequestStatus) -> Error {
            Error(
                errorDescription: "Invalid status received: (\(status.description)).",
                failureReason: "Invalid status."
            )
        }
    }
}
