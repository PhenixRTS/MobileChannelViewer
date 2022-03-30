//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

extension PhenixCore {
    final class MemberMediaController {
        private static let logger = OSLog(identifier: "MemberMediaController")

        private let queue: DispatchQueue
        private let associatedMemberID: String

        private var videoStateProvider: MemberStreamVideoStateProvider?
        private var videoFrameProvider: VideoFrameProvider?
        private var audioStateProvider: MemberStreamAudioStateProvider?
        private var audioVolumeProvider: MemberStreamAudioVolumeProvider?

        weak var delegate: MemberMediaDelegate?

        init(associatedMemberID: String, queue: DispatchQueue = .main) {
            self.queue = queue
            self.associatedMemberID = associatedMemberID
        }

        func setAudioVolumeProvider(_ provider: MemberStreamAudioVolumeProvider?) {
            audioVolumeProvider?.dispose()
            audioVolumeProvider = provider

            os_log(
                .debug,
                log: Self.logger,
                "%{public}s, Audio volume provider set: %{private}s",
                associatedMemberID,
                String(describing: provider)
            )

            provider?.onAudioVolumeProvided = { [weak self] decibel in
                self?.delegate?.audioLevelDidChange(decibel: decibel)
            }
        }

        func observeAudioVolume() {
            guard let provider = audioVolumeProvider else {
                return
            }

            os_log(.debug, log: Self.logger, "%{public}s, Observe audio volume", associatedMemberID)

            provider.observeAudioVolume()
        }

        func stopObservingAudioVolume() {
            guard let provider = audioVolumeProvider else {
                return
            }

            os_log(.debug, log: Self.logger, "%{public}s, Stop observing audio volume", associatedMemberID)

            provider.stopObservingAudioVolume()
        }

        func setAudioStateProvider(_ provider: MemberStreamAudioStateProvider?) {
            audioStateProvider?.dispose()
            audioStateProvider = provider

            os_log(
                .debug,
                log: Self.logger,
                "%{public}s, Audio state provider set: %{private}s",
                associatedMemberID,
                String(describing: provider)
            )

            provider?.stateChangeHandler = { [weak self] enabled in
                self?.delegate?.audioStateDidChange(enabled: enabled)
            }
            provider?.observeState()
        }

        func setVideoStateProvider(_ provider: MemberStreamVideoStateProvider?) {
            videoStateProvider?.dispose()
            videoStateProvider = provider

            os_log(
                .debug,
                log: Self.logger,
                "%{public}s, Video state provider set: %{private}s",
                associatedMemberID,
                String(describing: provider)
            )

            provider?.stateChangeHandler = { [weak self] enabled in
                self?.delegate?.videoStateDidChange(enabled: enabled)
            }
            provider?.observeState()
        }

        func setVideoFrameProvider(_ provider: VideoFrameProvider?) {
            videoFrameProvider?.dispose()
            videoFrameProvider = provider

            os_log(
                .debug,
                log: Self.logger,
                "%{public}s, Video frame provider set: %{private}s",
                associatedMemberID,
                String(describing: provider)
            )

            provider?.onFrameReceived = { [weak self] sampleBuffer in
                self?.delegate?.videoFrameReceived(sampleBuffer: sampleBuffer)
            }
        }

        func observeVideoFrames() {
            guard let provider = videoFrameProvider else {
                return
            }

            os_log(.debug, log: Self.logger, "%{public}s, Observe video frames", associatedMemberID)
            provider.observeFrames()
        }

        func stopObservingVideoFrames() {
            guard let provider = videoFrameProvider else {
                return
            }

            os_log(.debug, log: Self.logger, "%{public}s, Stop observing video frames", associatedMemberID)
            provider.stopObservingFrames()
        }
    }
}

// MARK: - Disposable
extension PhenixCore.MemberMediaController: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{public}s, Dispose", associatedMemberID)

        videoStateProvider?.dispose()
        videoStateProvider = nil

        audioStateProvider?.dispose()
        audioStateProvider = nil

        audioVolumeProvider?.dispose()
        audioVolumeProvider = nil

        videoFrameProvider?.dispose()
        videoFrameProvider = nil
    }
}

// MARK: - CustomStringConvertible
extension PhenixCore.MemberMediaController: CustomStringConvertible {
    public var description: String {
        "MemberMediaController(alias: \(String(describing: associatedMemberID)))"
    }
}

// MARK: - CustomDebugStringConvertible
extension PhenixCore.MemberMediaController: CustomDebugStringConvertible {
    public var debugDescription: String {
        """
        RoomMemberMediaController(
        alias: \(String(describing: associatedMemberID))), \
        videoStateProvider: \(String(describing: videoStateProvider)), \
        videoFrameProvider: \(String(describing: videoFrameProvider)), \
        audioStateProvider: \(String(describing: audioStateProvider)), \
        audioVolumeProvider: \(String(describing: audioVolumeProvider))
        )
        """
    }
}
