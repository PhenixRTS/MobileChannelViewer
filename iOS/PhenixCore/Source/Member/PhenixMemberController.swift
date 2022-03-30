//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

extension PhenixCore {
    final class MemberController {
        private static let logger = OSLog(identifier: "MemberController")

        private let queue: DispatchQueue
        private let member: Member
        private let memberMediaController: MemberMediaController
        private let subscriptionController: MemberSubscriptionController

        private var cancellables: Set<AnyCancellable>
        private var memberConnectionStateWatchdog: Watchdog?

        var memberSubscribesVideoStream: Bool {
            subscriptionController.subscribesVideoStream
        }

        weak var memberDelegate: (MemberSubscriptionInformationProvider & MemberUpdater)?
        weak var mediaDelegate: LocalMediaProvider?

        init(
            subscriptionController: MemberSubscriptionController,
            member: Member,
            queue: DispatchQueue = .main
        ) {
            self.queue = queue
            self.member = member
            self.cancellables = []

            self.memberMediaController = MemberMediaController(associatedMemberID: member.id, queue: queue)
            self.subscriptionController = subscriptionController

            self.memberMediaController.delegate = self
            self.subscriptionController.delegate = self
        }

        func start() {
            member.connectionStatePublisherInternal
                .removeDuplicates()
                .sink { [weak self] _ in
                    /*
                     When a member's connection state changes,
                     we also need to notify member's list publisher,
                     to refresh all of its subscribers.

                     For example, if the member's connection state changes from `active` to `away`,
                     then the user still should be visible, but if the state changes from `away` to `pending`,
                     then the user should be removed from the UI, therefore we need to refresh the whole member's list.
                     */
                    self?.memberDelegate?.memberDidUpdate()
                }
                .store(in: &cancellables)

            subscriptionController.start()
        }

        func observeAudioVolume() {
            memberMediaController.observeAudioVolume()
        }

        func stopObservingAudioVolume() {
            memberMediaController.stopObservingAudioVolume()
        }

        func observeVideoFrames() {
            memberMediaController.observeVideoFrames()
        }

        func stopObservingVideoFrames() {
            memberMediaController.stopObservingVideoFrames()
        }

        func renderVideo(layer: CALayer?) {
            member.renderVideo(layer: layer)
        }

        func renderThumbnailVideo(layer: CALayer?) {
            if layer == nil {
                /*
                 If the preview layer will be removed from the super layer,
                 then there is no need to continue frame observing.
                 */
                stopObservingVideoFrames()
            } else if member.secondaryPreviewLayer.superlayer == nil {
                 // If the preview layer hasn't been added to any other layers, we need to start observing frames.
                observeVideoFrames()
            }

            member.renderThumbnailVideo(layer: layer)
        }

        // MARK: - Media methods

        private func setupVideoStateProvider(stream: PhenixStream) {
            let provider = MemberStreamVideoStateProvider(associatedMemberID: member.id, stream: stream, queue: queue)
            memberMediaController.setVideoStateProvider(provider)
        }

        private func setupAudioStateProvider(stream: PhenixStream) {
            let provider = MemberStreamAudioStateProvider(associatedMemberID: member.id, stream: stream, queue: queue)
            memberMediaController.setAudioStateProvider(provider)
        }

        private func setupAudioVolumeProvider(renderer: PhenixRenderer, audioTracks: [PhenixMediaStreamTrack]) {
            let provider = MemberStreamAudioVolumeProvider(
                associatedMemberID: member.id,
                renderer: renderer,
                audioTracks: audioTracks,
                queue: queue
            )
            memberMediaController.setAudioVolumeProvider(provider)

            // TODO: Implement a way to start observing volume only when there are actual volume subscribers in the member model.
            // That way it would be more efficient.
            // We would not waste resources in cases,
            // when no-one is interested
            // in the current member volume.
            observeAudioVolume()
        }

        private func setupVideoFrameProvider(renderer: PhenixRenderer, videoTracks: [PhenixMediaStreamTrack]) {
            let provider = PhenixCore.VideoFrameProvider(renderer: renderer, videoTracks: videoTracks, queue: queue)
            memberMediaController.setVideoFrameProvider(provider)

            /*
             In case if the video frame provided is recreated,
             while it was already transmitting video frames
             to the layer, we need to automatically observe
             frames again.
             */
            if member.secondaryPreviewLayer.superlayer != nil {
                observeVideoFrames()
            }
        }

        private func removeVideoStateProvider() {
            memberMediaController.setVideoStateProvider(nil)
        }

        private func removeAudioStateProvider() {
            memberMediaController.setAudioStateProvider(nil)
        }

        private func removeAudioVolumeProvider() {
            memberMediaController.setAudioVolumeProvider(nil)
        }

        private func removeVideoFrameProvider() {
            member.dropLastSecondaryVideoFrame()
            memberMediaController.setVideoFrameProvider(nil)
        }
    }
}

// MARK: - Disposable
extension PhenixCore.MemberController: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{public}s, Dispose", member.id)

        member.dispose()
        memberMediaController.dispose()
        subscriptionController.dispose()
    }
}

// MARK: - RoomMemberMediaDelegate
extension PhenixCore.MemberController: MemberMediaDelegate {
    func audioStateDidChange(enabled: Bool) {
        member.setAudio(enabled: enabled)
    }

    func videoStateDidChange(enabled: Bool) {
        member.setVideo(enabled: enabled)
    }

    func audioLevelDidChange(decibel: Double) {
        let volume = PhenixCore.Member.Volume(rawValue: decibel)
        member.setVolume(volume)
    }

    func videoFrameReceived(sampleBuffer: CMSampleBuffer) {
        member.setSecondaryVideoFrame(sampleBuffer)
    }
}

// MARK: - Hashable
extension PhenixCore.MemberController: Hashable {
    public func hash(into hasher: inout Hasher) {
        hasher.combine(member)
    }
}

// MARK: - Equatable
extension PhenixCore.MemberController: Equatable {
    static func == (lhs: PhenixCore.MemberController, rhs: PhenixCore.MemberController) -> Bool {
        lhs.member == rhs.member
    }
}

// MARK: - MemberSubscriptionControllerDelegate
extension PhenixCore.MemberController: MemberSubscriptionControllerDelegate {
    /*
     After receiving information from the stream subscription,
     that the stream produces data, we need to subscribe to it
     to receive the media state updates, like, is audio enabled
     or disabled.
     For state tracking responsible is `MemberMediaController`.
     */

    func subscriptionController(
        _ controller: PhenixCore.MemberSubscriptionController,
        didReceiveDataFrom stream: PhenixStream
    ) {
        // This delegate method is triggered for self-members only.
        guard member.isSelf else {
            assertionFailure("subscriptionController(_:didReceiveDataFrom:) must be only triggered for self-members.")
            return
        }

        setupAudioStateProvider(stream: stream)
        setupVideoStateProvider(stream: stream)

        if let renderer = mediaDelegate?.getLocalMediaRenderer(),
           let audioTracks = mediaDelegate?.getLocalMediaAudioTracks() {
            setupAudioVolumeProvider(renderer: renderer, audioTracks: audioTracks)
        }
    }

    func subscriptionController(
        _ controller: PhenixCore.MemberSubscriptionController,
        didReceiveDataFrom stream: PhenixStream,
        with subscriptions: Set<MemberSubscriptionService.Subscription>
    ) {
        setupAudioStateProvider(stream: stream)
        setupVideoStateProvider(stream: stream)

        for subscription in subscriptions {
            if subscription.isAudioSubscription {
                setupAudioVolumeProvider(renderer: subscription.renderer, audioTracks: subscription.audioTracks)
            }

            if subscription.isVideoSubscription {
                setupVideoFrameProvider(renderer: subscription.renderer, videoTracks: subscription.videoTracks)
            }
        }
    }

    func shouldSubscriptionControllerSubscribeVideoStream(
        _ controller: PhenixCore.MemberSubscriptionController
    ) -> Bool {
        memberDelegate?.canMemberSubscribeForVideo() ?? false
    }
}
