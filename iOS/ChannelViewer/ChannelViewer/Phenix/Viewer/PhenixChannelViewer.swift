//
//  Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk
import UIKit

public final class PhenixChannelViewer {
    private let channelExpress: PhenixChannelExpress
    public weak var delegate: PhenixChannelViewerDelegate?

    public init(channelExpress: PhenixChannelExpress) {
        self.channelExpress = channelExpress
    }

    public func join(channelAlias: String?, videoLayer: CALayer, capabilities: [String] = PhenixConfiguration.capabilities) {
        let options = PhenixConfiguration.makeJoinChannelOptions(
            roomAlias: channelAlias,
            videoLayer: videoLayer,
            capabilities: capabilities
        )

        channelExpress.joinChannel(options, { [weak self] status, roomService in
            guard let self = self else { return }
            switch status {
            case .ok:
                guard let roomService = roomService else {
                    let error = Error(reason: "Missing PhenixRoomService")
                    self.delegate?.channelViewer(self, didFailToJoinWith: error)
                    return
                }
                self.delegate?.channelViewer(self, didJoin: roomService)

            default:
                let error = Error(reason: status.description)
                self.delegate?.channelViewer(self, didFailToJoinWith: error)
            }
        }) { [weak self] status, subscriber, renderer in
            guard let self = self else { return }
            switch status {
            case .ok:
                guard let subscriber = subscriber else {
                    let error = Error(reason: "Missing PhenixExpressSubscriber")
                    self.delegate?.channelViewer(self, didFailToSubscribeWith: error)
                    return
                }
                guard let renderer = renderer else {
                    let error = Error(reason: "Missing PhenixRenderer")
                    self.delegate?.channelViewer(self, didFailToSubscribeWith: error)
                    return
                }
                self.delegate?.channelViewer(self, didSubscribeWith: subscriber, renderer: renderer)

            case .noStreamPlaying:
                self.delegate?.channelViewerHasNoActiveStream(self)

            default:
                let error = Error(reason: status.description)
                self.delegate?.channelViewer(self, didFailToSubscribeWith: error)
            }
        }
    }
}

// MARK: - PhenixChannelViewer.Error
extension PhenixChannelViewer {
    public struct Error: Swift.Error, LocalizedError {
        public let reason: String
        public var errorDescription: String? { reason }
    }
}
