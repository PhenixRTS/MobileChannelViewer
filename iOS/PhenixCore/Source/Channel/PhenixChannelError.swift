//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.Channel {
    struct Error: LocalizedError {
        public var errorDescription: String?
        public var failureReason: String?
        public var recoverySuggestion: String?

        public static let missingRoomService = Error(
            errorDescription: "Room Service instance is missing.",
            failureReason: "Room Service is missing."
        )

        public static let missingPublisher = Error(
            errorDescription: "Publisher instance is missing.",
            failureReason: "Publisher is missing."
        )

        public static let missingPublishToken = Error(
            errorDescription: "Channel publish token is not provided.",
            failureReason: "Missing publish token.",
            recoverySuggestion: "Provide a valid publish token."
        )

        public static let missingStreamToken = Error(
            errorDescription: "Channel stream token is not provided.",
            failureReason: "Missing stream token.",
            recoverySuggestion: "Provide a valid stream token."
        )

        public static let channelAlreadyJoined = Error(
            errorDescription: "Current member is already joined to a channel.",
            failureReason: "Member already joined a channel."
        )

        public static let channelNotJoined = Error(
            errorDescription: "Current member is not joined to the channel.",
            failureReason: "Channel not joined.",
            recoverySuggestion: "Try to join to the channel first."
        )

        public static let mediaAlreadyPublishing = Error(
            errorDescription: "Current member is already publishing media to the channel.",
            failureReason: "Already publishing the media."
        )

        public static let channelAlreadyLeaving = Error(
            errorDescription: "Current member is already trying to leave the channel.",
            failureReason: "Already leaving the channel."
        )

        public static func invalid(_ status: PhenixRequestStatus) -> Error {
            Error(
                errorDescription: "Invalid status received: (\(status.description)).",
                failureReason: "Invalid status."
            )
        }
    }
}
