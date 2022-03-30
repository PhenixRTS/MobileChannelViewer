//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.TimeShift {
    struct Error: LocalizedError {
        public var errorDescription: String?
        public var failureReason: String?
        public var recoverySuggestion: String?

        public static let rendererNotSeekable = Error(
            errorDescription: "Renderer is not seekable.",
            failureReason: "Renderer is not seekable."
        )

        public static let rendererSeekingFailed = Error(
            errorDescription: "Renderer failed to seek.",
            failureReason: "Renderer failed to seek."
        )

        public static let pausingFailed = Error(
            errorDescription: "TimeShift could not pause the playback.",
            failureReason: "TimeShift pause failed.",
            recoverySuggestion: "While the playback isn't playing, it is not possible to pause."
        )

        public static let playingFailed = Error(
            errorDescription: "TimeShift could not play the playback.",
            failureReason: "TimeShift play failed.",
            recoverySuggestion: """
                TimeShift can only start playing if it meets one of the \
                following TimeShift.State: .ready, .paused, .ended, .seekingSucceeded.
                """
        )

        public static let stopingFailed = Error(
            errorDescription: "TimeShift could not stop the playback.",
            failureReason: "TimeShift stop failed.",
            recoverySuggestion: """
                TimeShift can only stop playing if it meets one of the \
                following TimeShift.State: .playing, .paused, .seeking, .seekingSucceeded, .ended.
                """
        )

        public static let seekingFailed = Error(
            errorDescription: "TimeShift could not seek the playback.",
            failureReason: "TimeShift seek failed.",
            recoverySuggestion: """
                TimeShift can only seek if it meets one of the \
                following TimeShift.State: .playing, .paused, .seeking, .seekingSucceeded, .ended.
                """
        )

        public static func invalid(_ status: PhenixRequestStatus) -> Error {
            Error(
                errorDescription: "Invalid status received: (\(status.description)).",
                failureReason: "Invalid status."
            )
        }
    }
}
