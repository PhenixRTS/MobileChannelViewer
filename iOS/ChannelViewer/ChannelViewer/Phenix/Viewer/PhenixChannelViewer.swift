//
//  Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk
import UIKit
import AVKit

public final class PhenixChannelViewer: NSObject {
    private let channelExpress: PhenixChannelExpress
    public weak var delegate: PhenixChannelViewerDelegate?

    private var pipVideoCallViewController: AVPictureInPictureController?

    private var subscriber: PhenixExpressSubscriber?
    private var renderer: PhenixRenderer?
    private var bandwidthDisposable: PhenixDisposable?
    private var videoStreamTrack: PhenixMediaStreamTrack?
    private var authenticationStatusDisposable: PhenixDisposable?

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

    public func limitBandwidth() {
        if let track = videoStreamTrack {
            bandwidthDisposable = track.limitBandwidth(300000) // 144p
        }
    }

    public func disposeBandwidthLimit() {
        bandwidthDisposable = nil
    }

    private func initializePiPController(with videoLayer: AVSampleBufferDisplayLayer) {
        if #available(iOS 15, *) {
            let contentSource = AVPictureInPictureController.ContentSource(
                sampleBufferDisplayLayer: videoLayer, playbackDelegate: self)

            let pipController = AVPictureInPictureController(contentSource: contentSource)
            self.pipVideoCallViewController = pipController
        }
    }

    public func join(videoLayer: CALayer, pipVideoLayer: AVSampleBufferDisplayLayer?) {
        if let pipVideoLayer = pipVideoLayer {
            initializePiPController(with: pipVideoLayer)
        }

        let options = PhenixConfiguration.makeJoinChannelOptions(
            with: pipVideoLayer == nil ? videoLayer : nil
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

                if let pipVideoLayer = pipVideoLayer, let track = subscriber.getVideoTracks().first {
                    self.videoStreamTrack = track

                    renderer.setFrameReadyCallback(track) { notification in
                        guard let notification = notification else { return }

                        notification.read({ inputFrame in
                            guard let videoFrame = inputFrame else { return }

                            videoFrame.makeFrameDisplayableImmediately()

                            DispatchQueue.main.async {
                                pipVideoLayer.enqueue(videoFrame)
                            }
                        })

                        notification.drop()
                    }
                }

                self.renderer = renderer
                self.subscriber = subscriber

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

extension PhenixChannelViewer : AVPictureInPictureSampleBufferPlaybackDelegate {
    public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, setPlaying playing: Bool) {
    }

    public func pictureInPictureControllerTimeRangeForPlayback(_ pictureInPictureController: AVPictureInPictureController) -> CMTimeRange {
        return CMTimeRange(start: .negativeInfinity, duration: .positiveInfinity)
    }

    public func pictureInPictureControllerIsPlaybackPaused(_ pictureInPictureController: AVPictureInPictureController) -> Bool {
        return false
    }

    public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {
    }

    public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, skipByInterval skipInterval: CMTime, completion completionHandler: @escaping () -> Void) {
        completionHandler()
    }

}
