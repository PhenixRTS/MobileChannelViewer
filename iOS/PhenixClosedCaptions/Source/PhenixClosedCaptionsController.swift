//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixCore

public final class PhenixClosedCaptionsController {
    public typealias Core = PhenixCoreMessageProvider & PhenixCoreChannelProvider

    private static let decoder = JSONDecoder()

    private let core: Core
    private let queue: DispatchQueue
    private let defaultMimeType = "application/Phenix-CC"

    private var cancellable: AnyCancellable?
    private var messageProvider: PhenixChannelMessageProvider?

    private weak var containerView: PhenixClosedCaptionsView?

    public weak var delegate: PhenixClosedCaptionsControllerDelegate?

    /// A Boolean value indicating whether the Closed Captions service is enabled.
    ///
    /// If set to `true` then Closed Captions controller will subscribe to receive the messages,
    /// or if set to `false` - unsubscribe from message retrieval.
    ///
    /// The default value of this property is `true`.
    ///
    /// - Tag: PhenixClosedCaptionsController.isEnabled
    public var isEnabled: Bool {
        didSet {
            guard isEnabled != oldValue else {
                return
            }
            closedCaptionsStateDidChange()
        }
    }

    public init(core: Core, queue: DispatchQueue? = nil) {
        self.core = core
        self.queue = queue ?? DispatchQueue(label: "Phenix.ClosedCaptions")
        self.isEnabled = true
    }

    public func subscribeForChannelMessages(alias: String) {
        subscribeForChannelMessages(alias: alias, mimeType: defaultMimeType)
    }

    public func subscribeForChannelMessages(alias: String, mimeType: String) {
        let provider = PhenixChannelMessageProvider(core: core, alias: alias, mimeType: mimeType)
        messageProvider = provider
        subscribeForMessages()
    }

    /// Clear saved reference objects, which may cause memory leaks if not released properly
    ///
    /// Always call this method before trying to destroy the Closed Caption service
    public func dispose() {
        cancellable = nil
        messageProvider?.dispose()
    }

    public func setContainerView(_ containerView: PhenixClosedCaptionsView?) {
        self.containerView = containerView
    }
}

// MARK: - Private methods
private extension PhenixClosedCaptionsController {
    /// Send Closed Captions to the delegate, if it is provided.
    /// - Parameter message: Received Closed Captions
    func deliverClosedCaptions(_ message: PhenixClosedCaptionsMessage) {
        os_log(.debug, log: .service, "Deliver closed captions to delegate")
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.closedCaptionsController(self, didReceive: message)
        }
    }

    /// Refresh current state by enabling or disabling the the subscription for the chat messages.
    ///
    /// Do not call this method manually.
    /// Instead update [isEnabled](x-source-tag://PhenixClosedCaptionsController.isEnabled) parameter to refresh the state.
    func closedCaptionsStateDidChange() {
        if isEnabled {
            os_log(.debug, log: .service, "Enable closed captions")
            subscribeForMessages()
        } else {
            os_log(.debug, log: .service, "Disable closed captions")
            dispose()
            containerView?.removeAllWindows()
        }
    }

    func process(_ message: PhenixClosedCaptionsMessage) {
        DispatchQueue.main.async { [weak self] in
            guard let containerView = self?.containerView else {
                return
            }

            // First we need to provide the text properties of the window.
            containerView.update(message.textUpdates, forWindow: message.windowIndex)

            // After the text properties has been provided, set the window properties,
            // so that the window size could be calculated correctly
            // based on the size of the caption text provided.
            if let window = message.windowUpdate {
                containerView.update(window, forWindow: message.windowIndex)
            }
        }
    }

    func subscribeForMessages() {
        guard let provider = messageProvider else {
            return
        }

        cancellable = provider.messagePublisher
            .receive(on: queue)
            .flatMap { message in
                Just(message)
                    .map(\.message)
                    .compactMap { $0.data(using: .utf8) }
                    .decode(type: PhenixClosedCaptionsMessage.self, decoder: Self.decoder)
                    .catch { error in
                        Empty(completeImmediately: true)
                            .eraseToAnyPublisher()
                    }
                    .eraseToAnyPublisher()
            }
            .sink { [weak self] message in
                self?.deliverClosedCaptions(message)
                self?.process(message)
            }

        provider.subscribe()
    }
}
