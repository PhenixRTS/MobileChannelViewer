//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

public final class PhenixClosedCaptionsService {
    private let chatService: PhenixRoomChatService
    private let decoder: JSONDecoder
    private let acceptableMimeTypes: [String] = ["application/Phenix-CC"]
    private var disposables: [PhenixDisposable] = []
    private weak var containerView: PhenixClosedCaptionsView?

    public weak var delegate: PhenixClosedCaptionsServiceDelegate?

    /// A Boolean value indicating whether the Closed Captions service is enabled.
    ///
    /// If set to `true` then Closed Captions service will subscribe to receive the messages, or if set to `false` - unsubscribe from message retrieval.
    ///
    /// The default value of this property is true for a newly ClosedCaptions service.
    ///
    /// - Tag: PhenixClosedCaptionsService_isEnabled
    public var isEnabled: Bool {
        didSet {
            guard isEnabled != oldValue else {
                return
            }
            closedCaptionsStateDidChange()
        }
    }

    public init(roomService: PhenixRoomService) {
        let batchSize: UInt = 0
        self.decoder = JSONDecoder()
        self.chatService = PhenixRoomChatServiceFactory.createRoomChatService(roomService, batchSize, acceptableMimeTypes)
        self.isEnabled = true

        self.subscribeForLastChatMessage()
    }

    /// Clear saved reference objects, which may cause memory leaks if not released properly
    ///
    /// Always call this method before trying to destroy the Closed Caption service
    public func dispose() {
        disposables.removeAll()
    }

    public func setContainerView(_ containerView: PhenixClosedCaptionsView?) {
        self.containerView = containerView
    }
}

// MARK: - Private methods
private extension PhenixClosedCaptionsService {
    /// Subscribe for chat messages
    ///
    /// Do not call this method manually.
    /// Instead set [isEnabled](x-source-tag://PhenixClosedCaptionsService_isEnabled) `= true` to subscribe for the messages.
    func subscribeForLastChatMessage() {
        os_log(.debug, log: .service, "Subscribe for closed captions")
        chatService.getObservableLastChatMessage()?.subscribe(lastChatMessageDidChange)?.append(to: &disposables)
    }

    /// Send Closed Captions to the delegate, if it is provided.
    /// - Parameter message: Received Closed Captions
    func deliverClosedCaptions(_ message: PhenixClosedCaptionsMessage) {
        os_log(.debug, log: .service, "Deliver closed captions to delegate")
        delegate?.closedCaptionsService(self, didReceive: message)
    }

    /// Refresh current state by enabling or disabling the the subscription for the chat messages.
    ///
    /// Do not call this method manually.
    /// Instead update [isEnabled](x-source-tag://PhenixClosedCaptionsService_isEnabled) parameter to refresh the state.
    func closedCaptionsStateDidChange() {
        if isEnabled {
            os_log(.debug, log: .service, "Enable closed captions")
            subscribeForLastChatMessage()
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

            // After the text properties has been provided, set the window properties, so that the window size could be calculated correctly based on the size of the caption text provided.
            if let window = message.windowUpdate {
                containerView.update(window, forWindow: message.windowIndex)
            }
        }
    }
}

// MARK: - Private observable callbacks
private extension PhenixClosedCaptionsService {
    func lastChatMessageDidChange(_ changes: PhenixObservableChange<PhenixChatMessage>?) {
        os_log(.debug, log: .service, "Did receive a message")
        guard let messageObject = changes?.value else {
            return
        }

        guard let message = messageObject.getObservableMessage()?.getValue() as String? else {
            os_log(.debug, log: .service, "Message is not a String object")
            return
        }

        guard let data = message.data(using: .utf8) else {
            os_log(.debug, log: .service, "Could not parse message into Data object using UTF8")
            return
        }

        do {
            let closedCaptions = try decoder.decode(PhenixClosedCaptionsMessage.self, from: data)
            deliverClosedCaptions(closedCaptions)
            process(closedCaptions)
        } catch {
            let e = error as NSError
            os_log(.debug, log: .service, "Could not parse message into PhenixClosedCaptionsMessage data model, error: %{PRIVATE}s", e.debugDescription)
        }
    }
}
