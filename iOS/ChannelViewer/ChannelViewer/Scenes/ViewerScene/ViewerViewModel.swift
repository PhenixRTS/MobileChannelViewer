//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixClosedCaptions
import PhenixCore

extension ViewerViewController {
    class ViewModel {
        private static let logger = OSLog(identifier: "ViewerViewController.ViewModel")

        private let core: PhenixCore
        private let session: AppSession
        private let closedCaptions: PhenixClosedCaptionsController

        private var channelsCancellable: AnyCancellable?
        private var channelStateCancellable: AnyCancellable?
        private var channelStateSubject: CurrentValueSubject<ChannelState, Never>

        private(set) lazy var channelStatePublisher = channelStateSubject
            .removeDuplicates()
            .eraseToAnyPublisher()

        var isClosedCaptionsEnabled: Bool { closedCaptions.isEnabled }
        var getStreamLayer: (() -> CALayer?)?

        init(core: PhenixCore, closedCaptions: PhenixClosedCaptionsController, session: AppSession) {
            self.core = core
            self.session = session
            self.closedCaptions = closedCaptions
            self.channelStateSubject = .init(.offline)
        }

        func joinChannel() {
            let configuration = PhenixCore.Channel.Configuration(alias: session.alias, streamToken: session.streamToken)
            core.joinToChannel(configuration: configuration)
        }

        func subscribeForClosedCaptions(_ view: PhenixClosedCaptionsView) {
            closedCaptions.setContainerView(view)
            closedCaptions.subscribeForChannelMessages(alias: session.alias)
        }

        func toggleClosedCaptions() {
            closedCaptions.isEnabled.toggle()
        }

        func subscribeForChannelEvents() {
            channelsCancellable = core.channelsPublisher
                .handleEvents(receiveOutput: { [weak self] _ in
                    self?.channelStateSubject.send(.offline)
                })
                .compactMap { [weak self] channels in
                    channels.first { $0.alias == self?.session.alias }
                }
                .sink { [weak self] channel in
                    self?.onJoinChannel(channel)
                }
        }

        private func onJoinChannel(_ channel: PhenixCore.Channel) {
            os_log(.debug, log: Self.logger, "Joined channel: %{private}s", channel.alias)

            channelStateCancellable = channel.statePublisher
                .sink { [weak self] state in
                    os_log(.debug, log: Self.logger, "Channel state: %{private}s", String(describing: state))

                    switch state {
                    case .streaming:
                        self?.channelStateSubject.send(.streaming)

                    default:
                        self?.channelStateSubject.send(.offline)
                    }
                }

            if let layer = getStreamLayer?() {
                os_log(.debug, log: Self.logger, "Render channel on layer: %{private}s", layer.description)
                core.renderVideo(alias: channel.alias, layer: layer)
            }
        }
    }
}

extension ViewerViewController.ViewModel {
    enum ChannelState {
        case streaming
        case offline
    }
}
