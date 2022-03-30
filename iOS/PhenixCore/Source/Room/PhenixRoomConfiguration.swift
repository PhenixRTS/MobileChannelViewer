//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.Room {
    struct Configuration: CustomStringConvertible {
        public var alias: String
        public var publishToken: String?
        public var audioStreamToken: String
        public var videoStreamToken: String
        public var memberName: String
        public var memberRole: PhenixCore.Member.Role
        public var maxVideoSubscriptions: Int

        /// Describes the room type when the room is initially created. This is optional parameter and only used when creating the room.
        public var roomType: RoomType?

        /// If set to `true`, then **PhenixCore** automatically subscribes to each ``PhenixCore/Member`` media,
        /// when this member appears in the ``PhenixCore/members`` list.
        ///
        /// Default value is `true`.
        ///
        /// If set to `false`, then to subscribe to all ``PhenixCore/Member`` media in the ``PhenixCore/members`` list,
        /// you must call ``PhenixCore/subscribeToRoomMembers()``.
        public var joinsSilently: Bool

        public var description: String {
            "Room.Configuration(alias: \(alias))"
        }

        public init(
            alias: String,
            publishToken: String?,
            audioStreamToken: String,
            videoStreamToken: String,
            memberName: String,
            memberRole: PhenixCore.Member.Role,
            maxVideoSubscriptions: Int,
            joinSilently: Bool = true,
            roomType: RoomType? = nil
        ) {
            self.alias = alias
            self.publishToken = publishToken
            self.audioStreamToken = audioStreamToken
            self.videoStreamToken = videoStreamToken
            self.memberName = memberName
            self.memberRole = memberRole
            self.joinsSilently = joinSilently
            self.maxVideoSubscriptions = maxVideoSubscriptions
            self.roomType = roomType
        }
    }
}

public extension PhenixCore.Room.Configuration {
    enum RoomType {
        case directChat, multiPartyChat, moderatedChat, townHall, channel

        var phenixRoomType: PhenixRoomType {
            switch self {
            case .directChat:
                return .directChat
            case .multiPartyChat:
                return .multiPartyChat
            case .moderatedChat:
                return .moderatedChat
            case .townHall:
                return .townHall
            case .channel:
                return .channel
            }
        }
    }
}
