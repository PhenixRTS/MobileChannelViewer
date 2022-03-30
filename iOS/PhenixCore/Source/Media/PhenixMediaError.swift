//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore {
    struct MediaError: LocalizedError {
        public var errorDescription: String?
        public var failureReason: String?
        public var recoverySuggestion: String?

        public static let missingUserMediaStream = MediaError(
            errorDescription: "User media stream instance is missing.",
            failureReason: "User media stream is missing."
        )

        public static let missingRenderer = MediaError(
            errorDescription: "Renderer instance is missing.",
            failureReason: "Renderer is missing."
        )

        public static func invalid(_ status: PhenixRequestStatus) -> MediaError {
            MediaError(
                errorDescription: "Invalid status received: (\(status.description)).",
                failureReason: "Invalid status."
            )
        }
    }
}
