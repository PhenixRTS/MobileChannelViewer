//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

extension PhenixCore {
    final class ChannelController {
        private static let logger = OSLog(identifier: "ChannelController")

        private let queue: DispatchQueue
        private let channelExpress: PhenixChannelExpress

        private var publishingChannel: PublishingChannel?

        // MARK: - Subjects
        private var eventSubject = PassthroughSubject<Channel.Event, Never>()
        private var channelsSubject = CurrentValueSubject<[Channel], Never>([])

        // MARK: - Publishers
        lazy var eventPublisher = eventSubject.eraseToAnyPublisher()
        lazy var channelsPublisher = channelsSubject.eraseToAnyPublisher()

        // MARK: - Other
        var channels: [Channel] { channelsSubject.value }

        init(channelExpress: PhenixChannelExpress, queue: DispatchQueue = .main) {
            self.queue = queue
            self.channelExpress = channelExpress
        }

        func createChannel(configuration: Channel.Configuration) {
            os_log(.debug, log: Self.logger, "%{private}s, Creating channel", configuration.alias)

            eventSubject.send(.channelCreating(alias: configuration.alias))

            let options = ChannelOptionsFactory.makeCreateChannelOptions(configuration: configuration)
            channelExpress.createChannel(options) { [weak self] status, _ in
                self?.queue.async {
                    guard let self = self else {
                        return
                    }

                    os_log(.debug, log: Self.logger, "%{private}s, Created channel with status: %{public}s", configuration.alias, status.description)

                    switch status {
                    case .ok:
                        self.eventSubject.send(.channelCreated(alias: configuration.alias))

                    default:
                        self.eventSubject.send(
                            .channelCreationFailed(alias: configuration.alias, error: .invalid(status))
                        )
                    }
                }
            }
        }

        func joinToChannel(configuration: Channel.Configuration) {
            os_log(.debug, log: Self.logger, " %{private}s, Joining to channel", configuration.alias)

            eventSubject.send(.channelJoining(alias: configuration.alias))

            guard channel(withAlias: configuration.alias) == nil else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Channel joining failed, already joined",
                    configuration.alias
                )

                eventSubject.send(.channelJoiningFailed(alias: configuration.alias, error: .channelAlreadyJoined))
                return
            }

            let channel = Channel(configuration: configuration, queue: queue)

            guard let streamToken = configuration.streamToken else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Channel joining failed, missing stream token",
                    configuration.alias
                )

                eventSubject.send(.channelJoiningFailed(alias: configuration.alias, error: .missingStreamToken))
                return
            }

            let rendererOptions = ChannelOptionsFactory.makeRendererOptions(configuration: configuration)
            let joinRoomOptions = ChannelOptionsFactory.makeJoinRoomOptions(configuration: configuration)
            let joinChannelOptions = ChannelOptionsFactory.makeJoinToChannelOptions(
                joinRoomOptions: joinRoomOptions,
                streamToken: streamToken,
                rendererLayer: channel.previewLayer,
                rendererOptions: rendererOptions
            )

            channelsSubject.value.append(channel)
            channel.prepareToJoin()

            /*
             !!! Important !!!

             When trying to join the channel, the first `subscriberCallback` event
             can be called before the first `joinChannelCallback` event.
             */

            // swiftlint:disable:next multiline_arguments
            channelExpress.joinChannel(joinChannelOptions) { [weak self] status, roomService in
                self?.queue.async {
                    guard let self = self else {
                        return
                    }

                    channel.joinChannelHandler(status: status, roomService: roomService)

                    switch status {
                    case .ok:
                        self.eventSubject.send(.channelJoined(alias: configuration.alias))

                    default:
                        self.eventSubject.send(
                            .channelJoiningFailed(alias: configuration.alias, error: .invalid(status))
                        )
                    }
                }
            } _: { [weak self, weak channel] status, subscriber, renderer in
                self?.queue.async {
                    channel?.subscriberHandler(status: status, subscriber: subscriber, renderer: renderer)
                }
            }
        }

        func publishToChannel(configuration: Channel.Configuration, userMediaStream: PhenixUserMediaStream) {
            os_log(.debug, log: Self.logger, "%{private}s, Publishing to channel", configuration.alias)

            eventSubject.send(.channelPublishing(alias: configuration.alias))

            guard publishingChannel == nil else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Channel publishing failed, already publishing media",
                    configuration.alias
                )

                eventSubject.send(
                    .channelPublishingFailed(alias: configuration.alias, error: .mediaAlreadyPublishing)
                )
                return
            }

            guard let publishToken = configuration.publishToken else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Channel publishing failed, missing publish token",
                    configuration.alias
                )

                eventSubject.send(.channelPublishingFailed(alias: configuration.alias, error: .missingPublishToken))
                return
            }

            let channelOptions = ChannelOptionsFactory.makeCreateChannelOptions(configuration: configuration)

            let publishOptions = PublishOptionsFactory.makePublishOptions(
                userMediaStream: userMediaStream,
                publishToken: publishToken
            )

            let publishToRoomOptions = ChannelOptionsFactory.makePublishToChannelOptions(
                channelOptions: channelOptions,
                publishOptions: publishOptions
            )

            channelExpress.publish(toChannel: publishToRoomOptions) { [weak self] status, roomService, publisher in
                self?.queue.async {
                    guard let self = self else {
                        return
                    }

                    os_log(
                        .debug,
                        log: Self.logger,
                        "%{private}s, Published to channel with status: %{public}s",
                        configuration.alias,
                        status.description
                    )

                    switch status {
                    case .ok:
                        guard let roomService = roomService else {
                            self.eventSubject.send(
                                .channelPublishingFailed(alias: configuration.alias, error: .missingRoomService)
                            )
                            return
                        }

                        guard let publisher = publisher else {
                            self.eventSubject.send(
                                .channelPublishingFailed(alias: configuration.alias, error: .missingPublisher)
                            )
                            return
                        }

                        self.onChannelPublish(roomService: roomService, publisher: publisher, configuration: configuration)
                        self.eventSubject.send(.channelPublished(alias: configuration.alias))

                    default:
                        self.eventSubject.send(
                            .channelPublishingFailed(alias: configuration.alias, error: .invalid(status))
                        )
                    }
                }
            }
        }

        func stopPublishing() {
            os_log(.debug, log: Self.logger, "Stop publishing")

            publishingChannel?.leave { [weak self] in
                self?.publishingChannel = nil
            }
        }

        func selectChannel(alias: String, isSelected: Bool) {
            channel(withAlias: alias)?.setSelection(enabled: isSelected)
        }

        func renderVideo(alias: String, layer: CALayer?) {
            channel(withAlias: alias)?.renderVideo(layer: layer)
        }

        func renderThumbnailVideo(alias: String, layer: CALayer?) {
            channel(withAlias: alias)?.renderThumbnailVideo(layer: layer)
        }

        func setBandwidthLimitation(_ bandwidth: UInt64, alias: String) {
            channel(withAlias: alias)?.setBandwidthLimitation(bandwidth)
        }

        func removeBandwidthLimitation(alias: String) {
            channel(withAlias: alias)?.removeBandwidthLimitation()
        }

        func leaveChannel(alias: String) {
            guard let channel = channel(withAlias: alias) else {
                return
            }

            os_log(.debug, log: Self.logger, "%{private}s, Leave channel", alias)

            channel.leave { [weak self] in
                self?.queue.async {
                    os_log(.debug, log: Self.logger, "%{private}s, Channel left", channel.alias)

                    if let index = self?.channels.firstIndex(where: { $0.alias == channel.alias }) {
                        self?.channelsSubject.value
                            .remove(at: index)
                            .dispose()
                    }
                }
            }
        }

        func setAudioEnabled(alias: String, enabled: Bool) {
            channel(withAlias: alias)?.setAudio(enabled: enabled)
        }

        func roomService(withAlias alias: String) -> PhenixRoomService? {
            channel(withAlias: alias)?.roomService
        }

        func channel(withAlias alias: String) -> PhenixCore.Channel? {
            channels.first { $0.alias == alias }
        }

        // MARK: - Private methods

        /// Sets up the publisher for the channel and the channel service from the publisher.
        /// - Parameters:
        ///   - roomService: Room service for the currently published channel.
        ///   - publisher: Publisher to the channel.
        ///   - configuration: Channel configuration.
        private func onChannelPublish(
            roomService: PhenixRoomService,
            publisher: PhenixExpressPublisher,
            configuration: Channel.Configuration
        ) {
            let channel = PublishingChannel(configuration: configuration)
            publishingChannel = channel
            channel.setPublisher(publisher, roomService: roomService)
        }
    }
}

// MARK: - Disposable
extension PhenixCore.ChannelController: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "Dispose")

        channelsSubject.value.forEach { $0.dispose() }
        channelsSubject.value.removeAll()
    }
}
