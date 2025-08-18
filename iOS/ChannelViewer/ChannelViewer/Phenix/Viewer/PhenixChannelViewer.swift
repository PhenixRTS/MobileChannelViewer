//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import AVKit
import PhenixSdk
import UIKit

public final class PhenixChannelViewer: NSObject {
    private let channelExpress: PhenixChannelExpress
    public weak var delegate: PhenixChannelViewerDelegate?

    private var subscriber: PhenixExpressSubscriber?
    private var renderer: PhenixRenderer?
    private var bandwidthDisposable: PhenixDisposable?
    private var videoStreamTrack: PhenixMediaStreamTrack?
    private var authenticationStatusDisposable: PhenixDisposable?

    private var pipController: AVPictureInPictureController?

    public init(channelExpress: PhenixChannelExpress) {
        self.channelExpress = channelExpress
        super.init()
        self.observeAuthenticationStatus()
    }

    private func observeAuthenticationStatus() {
        authenticationStatusDisposable = channelExpress.pcastExpress.getObservableAuthenticationStatus().subscribe { [weak self] change in
            guard let self = self else { return }
            let status = PhenixAuthenticationStatus(rawValue: change!.value.intValue)!

            if PhenixAuthenticationStatus.unauthenticated == status {
                self.handleUnauthenticatedStatus()
            }
        }
    }

    private func handleUnauthenticatedStatus() {
        // You can fetch a new authentication token and set it using setAuthenticationToken method to re-authenticate the SDK
        AppDelegate.terminate(
            afterDisplayingAlertWithTitle: "Failed to authenticate the Phenix SDK. The provided authentication token has expired",
            message: "Application will be terminated."
        )
    }

    public func setupPictureInPictureOverlay() {
        guard #available(iOS 15, *) else {
            return
        }

        renderer?.setPictureInPictureContentSourceChangedCallback { [weak self] pipContentSource in
            guard let self = self else {
                return
            }

            guard let pipContentSource = pipContentSource else {
                return
            }

            if let pipController = pipController {
                pipController.contentSource = pipContentSource
            } else {
                pipController = AVPictureInPictureController(contentSource: pipContentSource)
                pipController?.delegate = self
            }
        }
    }

    public func join(videoLayer: CALayer) {
        let options = PhenixConfiguration.makeJoinChannelOptions(with: videoLayer)

        channelExpress.joinChannel(options, { [weak self] status, roomService in
            guard let self = self else { return }
            onChannelJoined(status: status, roomService: roomService)
        }) { [weak self] status, subscriber, renderer in
            guard let self = self else { return }
            onStreamSubscribed(status: status, subscriber: subscriber, renderer: renderer)
        }
    }

    private func onChannelJoined(status: PhenixRequestStatus, roomService: PhenixRoomService?) {
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
    }

    private func onStreamSubscribed(status: PhenixRequestStatus, subscriber: PhenixExpressSubscriber?, renderer: PhenixRenderer?) {
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

            if let track = subscriber.getVideoTracks().first {
                self.videoStreamTrack = track
            }

            self.renderer = renderer
            self.subscriber = subscriber

            DispatchQueue.main.async { [weak self] in
                self?.setupPictureInPictureOverlay()
            }

            self.delegate?.channelViewer(self, didSubscribeWith: subscriber, renderer: renderer)

        case .noStreamPlaying:
            self.delegate?.channelViewerHasNoActiveStream(self)

        default:
            let error = Error(reason: status.description)
            self.delegate?.channelViewer(self, didFailToSubscribeWith: error)
        }
    }

    private func limitBandwidth() {
        if let track = videoStreamTrack {
            bandwidthDisposable = track.limitBandwidth(520000) // 360p
        }
    }

    private func disposeBandwidthLimit() {
        bandwidthDisposable = nil
    }
}

// MARK: - PhenixChannelViewer.Error

public extension PhenixChannelViewer {
    struct Error: Swift.Error, LocalizedError {
        public let reason: String
        public var errorDescription: String? { reason }
    }
}

extension PhenixChannelViewer: AVPictureInPictureControllerDelegate {
    public func pictureInPictureControllerWillStartPictureInPicture(_: AVPictureInPictureController) {
        limitBandwidth()
    }

    public func pictureInPictureControllerDidStartPictureInPicture(_: AVPictureInPictureController) {}

    public func pictureInPictureController(_: AVPictureInPictureController, failedToStartPictureInPictureWithError _: Swift.Error) {}

    public func pictureInPictureControllerWillStopPictureInPicture(_: AVPictureInPictureController) {
        disposeBandwidthLimit()
    }

    public func pictureInPictureControllerDidStopPictureInPicture(_: AVPictureInPictureController) {}

    public func pictureInPictureController(_: AVPictureInPictureController,
                                           restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void)
    {
        // Restore the user interface.
        completionHandler(true)
    }
}
