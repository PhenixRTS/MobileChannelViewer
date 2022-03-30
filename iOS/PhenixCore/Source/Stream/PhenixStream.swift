//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

public extension PhenixCore {
    final class Stream: PhenixTimeShifter {
        private static let logger = OSLog(identifier: "Stream")

        private let queue: DispatchQueue
        private let propertyPublisherQueue: DispatchQueue

        private(set) var renderer: PhenixRenderer?
        private var subscriber: PhenixExpressSubscriber?
        private var configuration: Configuration
        private var bandwidthLimitation: UInt64?
        private var secondaryVideoFrameProvider: VideoFrameProvider?

        private var stateSubject = CurrentValueSubject<State, Never>(.offline)
        private var isSelectedSubject = CurrentValueSubject<Bool, Never>(false)
        private var isAudioEnabledSubject = CurrentValueSubject<Bool, Never>(false)
        private var isVideoEnabledSubject = CurrentValueSubject<Bool, Never>(false)

        private var timeShiftStateSubject = CurrentValueSubject<TimeShift.State, Never>(.idle)
        private var timeShiftHeadSubject = CurrentValueSubject<TimeInterval, Never>(0)

        private var bandwidthLimitationDisposables: [PhenixDisposable] = []

        let previewLayer: VideoLayer
        let secondaryPreviewLayer: VideoLayer

        public var id: String { configuration.id }
        public var state: State { stateSubject.value }
        public var isSelected: Bool { isSelectedSubject.value }
        public var timeShiftHead: TimeInterval { timeShiftHeadSubject.value }
        public var timeShiftState: TimeShift.State { timeShiftStateSubject.value }
        public var isAudioEnabled: Bool { isAudioEnabledSubject.value }
        public var isVideoEnabled: Bool { isVideoEnabledSubject.value }

        public lazy var statePublisher: AnyPublisher<State, Never> = stateSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var isAudioEnabledPublisher: AnyPublisher<Bool, Never> = isAudioEnabledSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var isVideoEnabledPublisher: AnyPublisher<Bool, Never> = isVideoEnabledSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var isSelectedPublisher: AnyPublisher<Bool, Never> = isSelectedSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var timeShiftStatePublisher: AnyPublisher<TimeShift.State, Never> = timeShiftStateSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var timeShiftHeadPublisher: AnyPublisher<TimeInterval, Never> = timeShiftHeadSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        init(configuration: Configuration, queue: DispatchQueue, publisherQueue: DispatchQueue = .main) {
            self.queue = queue
            self.configuration = configuration

            self.previewLayer = VideoLayer()
            self.secondaryPreviewLayer = VideoLayer()
            self.secondaryPreviewLayer.videoGravity = .resizeAspectFill

            self.propertyPublisherQueue = publisherQueue
        }

        func prepareToSubscribe() {
            stateSubject.send(.joining)
        }

        func handleSubscription(status: PhenixRequestStatus, subscriber: PhenixExpressSubscriber?) {
            os_log(
                .debug,
                log: Self.logger,
                "%{public}s, Stream subscription state did change, state: %{public}s",
                id,
                status.description
            )

            self.subscriber = subscriber
            self.renderer = subscriber?.createRenderer()

            setupSecondaryVideoFrameProvider()

            switch status {
            case .ok:
                let status = renderer?.startSuspended(previewLayer) ?? .failed

                if status == .ok {
                    stateSubject.send(.ready)
                } else {
                    stateSubject.send(.offline)
                }

            case .noStreamPlaying:
                stateSubject.send(.noStream)

            default:
                stateSubject.send(.offline)
            }
        }

        func setAudio(enabled: Bool) {
            os_log(.debug, log: Self.logger, "%{public}s, Set audio: %{public}s", id, enabled.description)

            if enabled {
                renderer?.unmuteAudio()
            } else {
                renderer?.muteAudio()
            }

            isAudioEnabledSubject.send(enabled)
        }

        func setVideo(enabled: Bool) {
            os_log(.debug, log: Self.logger, "%{public}s, Set video: %{public}s", id, enabled.description)

            if enabled {
                subscriber?.enableVideo()
            } else {
                subscriber?.disableVideo()
            }

            isVideoEnabledSubject.send(enabled)
        }

        func setSelection(enabled: Bool) {
            os_log(.debug, log: Self.logger, "%{public}s, Set selection: %{public}s", id, enabled.description)
            isSelectedSubject.send(enabled)
        }

        func observeSecondaryVideoFrames() {
            secondaryVideoFrameProvider?.observeFrames()
        }

        func stopObservingSecondaryVideoFrames() {
            secondaryVideoFrameProvider?.stopObservingFrames()
        }

        func requestLastSecondaryVideoFrameRendered() {
            secondaryVideoFrameProvider?.requestLastVideoFrameRendered()
        }

        func renderVideo(layer: CALayer?) {
            os_log(.debug, log: Self.logger, "%{public}s, Render on surface: %{public}s", id, String(describing: layer))
            previewLayer.set(on: layer)
        }

        func renderThumbnailVideo(layer: CALayer?) {
            os_log(.debug, log: Self.logger, "%{public}s, Render on image: %{public}s", id, String(describing: layer))

            guard layer != secondaryPreviewLayer.superlayer else {
                return
            }

            if layer == nil {
                /*
                 If the preview layer will be removed from the super layer,
                 then there is no need to continue frame observing.
                 */
                stopObservingSecondaryVideoFrames()
                secondaryPreviewLayer.flushAndRemoveImage()
            } else if secondaryPreviewLayer.superlayer == nil {
                /*
                 If the preview layer hasn't been added to any other layers,
                 we need to start observing frames.
                 */
                observeSecondaryVideoFrames()
            }

            secondaryPreviewLayer.set(on: layer)

            requestLastSecondaryVideoFrameRendered()
        }

        func setBandwidthLimitation(_ bandwidth: UInt64) {
            bandwidthLimitationDisposables.removeAll()

            guard let videoTracks = subscriber?.getVideoTracks() else {
                return
            }

            os_log(.debug, log: Self.logger, "%{private}s, Set bandwidth limitation to %{private}d", id, bandwidth)

            videoTracks.forEach { track in
                if let disposable = track.limitBandwidth(bandwidth) {
                    bandwidthLimitationDisposables.append(disposable)
                }
            }

            bandwidthLimitation = bandwidth
        }

        func removeBandwidthLimitation() {
            os_log(.debug, log: Self.logger, "%{private}s, Remove bandwidth limitation", id)
            bandwidthLimitationDisposables.removeAll()
            bandwidthLimitation = nil
        }

        func subscribe(_ timeShift: PhenixCore.TimeShift) {
            timeShift.playbackStatePublisher.receive(subscriber: AnySubscriber(timeShiftStateSubject))
            timeShift.playbackHeadPublisher.receive(subscriber: AnySubscriber(timeShiftHeadSubject))

            if let bandwidthLimitation = bandwidthLimitation {
                timeShift.setBandwidthLimitation(bandwidthLimitation)
            }
        }

        // MARK: - Private methods

        private func setupSecondaryVideoFrameProvider() {
            secondaryPreviewLayer.flushAndRemoveImage()

            secondaryVideoFrameProvider?.dispose()
            secondaryVideoFrameProvider = nil

            guard let renderer = renderer else {
                return
            }

            guard let videoTracks = subscriber?.getVideoTracks() else {
                return
            }

            let provider = VideoFrameProvider(renderer: renderer, videoTracks: videoTracks, queue: queue)
            secondaryVideoFrameProvider = provider

            provider.onFrameReceived = { [weak self] sampleBuffer in
                guard let self = self else {
                    return
                }

                if self.secondaryPreviewLayer.isReadyForMoreMediaData {
                    self.secondaryPreviewLayer.enqueue(sampleBuffer)
                }
            }

            if secondaryPreviewLayer.superlayer != nil {
                /*
                 In case if the secondary preview layer is already
                 set on the UI, then we can automatically start
                 observing the frames.
                 */
                provider.observeFrames()
            }
        }
    }
}

// MARK: - Disposable
extension PhenixCore.Stream: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose", id)

        bandwidthLimitationDisposables.removeAll()
    }
}

// MARK: - Equatable
extension PhenixCore.Stream: Equatable {
    public static func == (lhs: PhenixCore.Stream, rhs: PhenixCore.Stream) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Hashable
extension PhenixCore.Stream: Hashable {
    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}
