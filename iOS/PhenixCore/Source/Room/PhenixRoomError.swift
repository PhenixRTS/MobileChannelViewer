//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.Room {
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
            errorDescription: "Room publish token is not provided.",
            failureReason: "Missing publish token.",
            recoverySuggestion: "Provide a valid publish token."
        )

        public static let roomAlreadyJoined = Error(
            errorDescription: "Current member is already joined to a room.",
            failureReason: "Member already joined a room.",
            recoverySuggestion: "Before trying to joining a room, please leave currently joined room."
        )

        public static let roomNotJoined = Error(
            errorDescription: "Current member is not joined to the room.",
            failureReason: "Room not joined.",
            recoverySuggestion: "Try to join to the room first."
        )

        public static let mediaAlreadyPublishing = Error(
            errorDescription: "Current member is already publishing media to the room.",
            failureReason: "Already publishing the media."
        )

        public static let roomAlreadyLeaving = Error(
            errorDescription: "Current member is already trying to leave the room.",
            failureReason: "Already leaving the room."
        )

        public static let blockedRemoteMemberMediaChange = Error(
            errorDescription: "Remote member's media state cannot be changed.",
            failureReason: "Can't change remote member's media state."
        )

        public static func invalid(_ status: PhenixRequestStatus) -> Error {
            Error(
                errorDescription: "Invalid status received: (\(status.description)).",
                failureReason: "Invalid status."
            )
        }
    }
}
