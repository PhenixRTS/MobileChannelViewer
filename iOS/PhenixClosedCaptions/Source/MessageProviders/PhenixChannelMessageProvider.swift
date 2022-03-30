//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import PhenixCore

class PhenixChannelMessageProvider {
    typealias Core = PhenixCoreMessageProvider & PhenixCoreChannelProvider

    private let core: Core
    private let alias: String
    private let mimeType: String

    private var channelListCancellable: AnyCancellable?
    private var channelStateCancellable: AnyCancellable?
    private var channelMessageCancellable: AnyCancellable?

    private var messageSubject = PassthroughSubject<PhenixCore.Message, Never>()

    lazy var messagePublisher = messageSubject
        .receive(on: DispatchQueue.main)
        .eraseToAnyPublisher()

    init(core: Core, alias: String, mimeType: String) {
        self.core = core
        self.alias = alias
        self.mimeType = mimeType
    }

    func subscribe() {
        subscribeForChannel()
    }

    func dispose() {
        channelListCancellable = nil
        channelStateCancellable = nil
        channelMessageCancellable = nil
    }
}

private extension PhenixChannelMessageProvider {
    func subscribeForChannel() {
        channelListCancellable = core.channelsPublisher
            .compactMap { [weak self] channels in
                channels.first { $0.alias == self?.alias }
            }
            .sink { [weak self] channel in
                self?.subscribeForChannelState(channel)
            }
    }

    func subscribeForChannelState(_ channel: PhenixCore.Channel) {
        channelStateCancellable = channel.statePublisher
            .filter { $0 == .streaming }
            .sink { [weak self] state in
                self?.subscribeForChannelMessages()
            }
    }

    func subscribeForChannelMessages() {
        channelMessageCancellable = core.messagesPublisher
            .compactMap(\.last)
            .filter { [weak self] in
                $0.mimeType == self?.mimeType
            }
            .sink { [weak self] message in
                self?.messageSubject.send(message)
            }

        let configuration = PhenixCore.Message.Configuration(batchSize: 0, mimeType: mimeType)
        core.subscribeToMessages(alias: alias, configuration: configuration)
    }
}
