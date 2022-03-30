//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

enum ChannelOptionsFactory {
    static func makeChannelExpressOptions(roomExpressOptions: PhenixRoomExpressOptions) -> PhenixChannelExpressOptions {
        PhenixChannelExpressFactory
            .createChannelExpressOptionsBuilder()
            .withRoomExpressOptions(roomExpressOptions)
            .buildChannelExpressOptions()
    }

    static func makeCreateChannelOptions(configuration: PhenixCore.Channel.Configuration) -> PhenixChannelOptions {
        PhenixRoomServiceFactory.createChannelOptionsBuilder()
            .withAlias(configuration.alias)
            .withName(configuration.alias)
            .buildChannelOptions()
    }

    static func makeJoinRoomOptions(configuration: PhenixCore.Channel.Configuration) -> PhenixJoinRoomOptions {
        PhenixRoomExpressFactory
            .createJoinRoomOptionsBuilder()
            .withRoomAlias(configuration.alias)
            .buildJoinRoomOptions()
    }

    static func makeRendererOptions(configuration: PhenixCore.Channel.Configuration) -> PhenixRendererOptions {
        let options = PhenixRendererOptions()
        options.aspectRatioMode = configuration.videoAspectRatio.phenixAspectRatio
        return options
    }

    static func makeJoinToChannelOptions(
        joinRoomOptions: PhenixJoinRoomOptions,
        streamToken: String,
        rendererLayer: CALayer,
        rendererOptions: PhenixRendererOptions
    ) -> PhenixJoinChannelOptions {
        PhenixChannelExpressFactory
            .createJoinChannelOptionsBuilder()
            .withStreamToken(streamToken)
            .withSkipRetryOnUnauthorized()
            .withJoinRoomOptions(joinRoomOptions)
            .withStreamSelectionStrategy(.highAvailability)
            .withRenderer(rendererLayer)
            .withRendererOptions(rendererOptions)
            .buildJoinChannelOptions()
    }

    static func makePublishToChannelOptions(
        channelOptions: PhenixChannelOptions,
        publishOptions: PhenixPublishOptions
    ) -> PhenixPublishToChannelOptions {
        let optionBuilder: PhenixPublishToChannelOptionsBuilder = PhenixChannelExpressFactory
            .createPublishToChannelOptionsBuilder()

        optionBuilder
            .withChannelOptions(channelOptions)
            .withPublishOptions(publishOptions)

        return optionBuilder.buildPublishToChannelOptions()
    }
}
