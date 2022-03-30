//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

enum RoomOptionsFactory {
    static func makeRoomExpressOptions(pcastExpressOptions: PhenixPCastExpressOptions) -> PhenixRoomExpressOptions {
        PhenixRoomExpressFactory
            .createRoomExpressOptionsBuilder()
            .withPCastExpressOptions(pcastExpressOptions)
            .buildRoomExpressOptions()
    }

    static func makeCreateRoomOptions(configuration: PhenixCore.Room.Configuration) -> PhenixRoomOptions {
        let roomOptionsBuilder: PhenixRoomOptionsBuilder = PhenixRoomServiceFactory
            .createRoomOptionsBuilder()

        if let roomType = configuration.roomType?.phenixRoomType {
            roomOptionsBuilder.withType(roomType)
        }

        return roomOptionsBuilder
            .withAlias(configuration.alias)
            .withName(configuration.alias)
            .buildRoomOptions()
    }

    static func makeJoinToRoomOptions(configuration: PhenixCore.Room.Configuration) -> PhenixJoinRoomOptions {
        let joinRoomOptionBuilder: PhenixJoinRoomOptionsBuilder = PhenixRoomExpressFactory
            .createJoinRoomOptionsBuilder()

        return joinRoomOptionBuilder
            .withRoomAlias(configuration.alias)
            .withRole(configuration.memberRole.phenixMemberRole)
            .withScreenName(configuration.memberName)
            .buildJoinRoomOptions()
    }

    static func makePublishToRoomOptions(
        roomOptions: PhenixRoomOptions,
        publishOptions: PhenixPublishOptions,
        configuration: PhenixCore.Room.Configuration,
        roomID: String
    ) -> PhenixPublishToRoomOptions {
        let optionBuilder: PhenixPublishToRoomOptionsBuilder = PhenixRoomExpressFactory.createPublishToRoomOptionsBuilder()

        optionBuilder
            .withRoomId(roomID)
            .withRoomOptions(roomOptions)
            .withPublishOptions(publishOptions)
            .withScreenName(configuration.memberName)
            .withMemberRole(configuration.memberRole.phenixMemberRole)

        return optionBuilder.buildPublishToRoomOptions()
    }
}
