//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

extension PhenixCore {
    final class MediaController {
        private static let logger = OSLog(identifier: "MediaController")

        private let queue: DispatchQueue
        private let pcastExpress: PhenixPCastExpress
        private let previewLayer: VideoLayer
        private let secondaryPreviewLayer: VideoLayer

        private var configuration: PhenixCore.MediaConfiguration = .default
        private var videoFrameProvider: VideoFrameProvider?

        private(set) var renderer: PhenixRenderer?
        private(set) var userMediaStream: PhenixUserMediaStream?

        // MARK: - Subjects
        private var eventSubject = PassthroughSubject<MediaEvent, Never>()

        // MARK: - Publishers
        lazy var eventPublisher = eventSubject.eraseToAnyPublisher()

        init(pcastExpress: PhenixPCastExpress, queue: DispatchQueue) {
            self.queue = queue
            self.pcastExpress = pcastExpress
            self.previewLayer = VideoLayer()
            self.secondaryPreviewLayer = VideoLayer()
            self.secondaryPreviewLayer.videoGravity = .resizeAspectFill
            /*
             We need to rotate the video, so that the secondary preview
             would display the same image as the primary preview.
             By default, it displays image flipped horizontally,
             and that feels incorrect when looking at yourself.
             */
            self.secondaryPreviewLayer.transform = CATransform3DMakeScale(-1.0, 1.0, 1.0)
        }

        func start(configuration: MediaConfiguration = .default) {
            setupUserMediaStream(configuration: configuration)
        }

        /// Set the main local video preview on the provided layer
        /// - Parameter layer: If the layer is provided, then the video will be set on it. If the layer is `nil`, then the existing video preview will be removed.
        func previewOnSurface(layer: CALayer?) {
            guard layer != previewLayer.superlayer else {
                return
            }

            previewLayer.set(on: layer)
        }

        /// Set the secondary local video preview on the provided layer
        /// - Parameter layer: If the layer is provided, then the video will be set on it. If the layer is `nil`, then the existing video preview will be removed.
        func previewOnImage(layer: CALayer?) {
            guard layer != secondaryPreviewLayer.superlayer else {
                return
            }

            if layer == nil {
                // If the preview layer will be removed from the super layer,
                // then there is no need to continue frame observing.
                videoFrameProvider?.stopObservingFrames()
                secondaryPreviewLayer.flushAndRemoveImage()
            } else if secondaryPreviewLayer.superlayer == nil {
                // If the preview layer hasn't been added to any other layers,
                // we need to start observing frames.
                videoFrameProvider?.observeFrames()
            }

            secondaryPreviewLayer.set(on: layer)
        }

        func isAudioEnabled() -> Bool {
            userMediaStream?.mediaStream.getAudioTracks().contains { $0.isEnabled() } ?? false
        }

        func isVideoEnabled() -> Bool {
            userMediaStream?.mediaStream.getVideoTracks().contains { $0.isEnabled() } ?? false
        }

        func setAudioEnabled(enabled: Bool) {
            userMediaStream?.mediaStream.getAudioTracks().forEach { $0.setEnabled(enabled) }
        }

        func setVideoEnabled(enabled: Bool) {
            userMediaStream?.mediaStream.getVideoTracks().forEach { $0.setEnabled(enabled) }
        }

        /// Toggle camera between the front-camera and the back-camera.
        func flipCamera() {
            configuration.cameraFacingMode.toggle()
            update(configuration)
        }

        /// Update Media configuration.
        /// - Parameter configuration: Media configuration.
        func update(_ configuration: PhenixCore.MediaConfiguration) {
            guard let userMediaStream = userMediaStream else {
                eventSubject.send(.mediaConfigurationUpdateFailed(error: .missingUserMediaStream))
                return
            }

            let options = configuration.makeOptions()
            let status = userMediaStream.apply(options)

            os_log(.debug, log: Self.logger, "Trying to set media configuration: %{private}s", configuration.description)
            os_log(.debug, log: Self.logger, "Media configuration update: %{private}s", status.description)

            if status == .ok {
                self.configuration = configuration
                eventSubject.send(.mediaConfigurationUpdated)
            } else {
                eventSubject.send(.mediaConfigurationUpdateFailed(error: .invalid(status)))
            }
        }

        // MARK: - Private methods

        /// At the moment when the local user media stream is created,
        /// device starts to record audio and video (depending on the configuration),
        /// and render the video on the main preview layer.
        ///
        /// - Parameters:
        ///   - configuration: Media configuration.
        private func setupUserMediaStream(configuration: MediaConfiguration = .default) {
            eventSubject.send(.mediaInitializing)

            self.configuration = configuration
            let options = configuration.makeOptions()

            os_log(.debug, log: Self.logger, "Trying to set Media options: %{private}s", configuration.description)
            pcastExpress.getUserMedia(options) { [weak self] status, userMediaStream in
                os_log(.debug, log: Self.logger, "UserMediaStream provided: %{private}s", status.description)
                self?.onGetUserMedia(status: status, userMediaStream: userMediaStream)
            }
        }

        private func setupSecondaryVideoFrameProvider() {
            secondaryPreviewLayer.flushAndRemoveImage()

            videoFrameProvider?.dispose()
            videoFrameProvider = nil

            guard let renderer = renderer else {
                return
            }

            guard let videoTracks = userMediaStream?.mediaStream.getVideoTracks() else {
                return
            }

            let provider = VideoFrameProvider(renderer: renderer, videoTracks: videoTracks, queue: queue)
            videoFrameProvider = provider

            provider.onFrameReceived = { [weak self] sampleBuffer in
                guard let self = self else {
                    return
                }

                if self.secondaryPreviewLayer.isReadyForMoreMediaData {
                    self.secondaryPreviewLayer.enqueue(sampleBuffer)
                }
            }

            if secondaryPreviewLayer.superlayer != nil {
                // In case if the secondary preview layer is already
                // set on the UI, then we can automatically start
                // observing the frames.
                provider.observeFrames()
            }
        }

        private func onGetUserMedia(status: PhenixRequestStatus, userMediaStream: PhenixUserMediaStream?) {
            self.userMediaStream = nil
            renderer = nil
            videoFrameProvider?.dispose()
            videoFrameProvider = nil

            guard status == .ok else {
                eventSubject.send(.mediaInitializationFailed(error: .invalid(status)))
                return
            }

            guard let userMediaStream = userMediaStream else {
                eventSubject.send(.mediaInitializationFailed(error: .missingUserMediaStream))
                return
            }

            self.userMediaStream = userMediaStream

            guard let renderer = userMediaStream.mediaStream.createRenderer() else {
                eventSubject.send(.mediaInitializationFailed(error: .missingRenderer))
                return
            }

            self.renderer = renderer

            let renderingStatus = renderer.start(previewLayer)
            os_log(.debug, log: Self.logger, "Media rendering started: %{private}s", renderingStatus.description)

            setupSecondaryVideoFrameProvider()

            eventSubject.send(.mediaInitialized)
        }
    }
}

// MARK: - Disposable
extension PhenixCore.MediaController: Disposable {
    func dispose() {
        videoFrameProvider?.stopObservingFrames()
        videoFrameProvider = nil

        renderer?.stop()
        renderer = nil

        userMediaStream?.mediaStream.stop()
        userMediaStream = nil
    }
}
