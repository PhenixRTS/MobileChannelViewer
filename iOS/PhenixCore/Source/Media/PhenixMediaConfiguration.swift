//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore {
    struct MediaConfiguration {
        public let isAudioEnabled: Bool
        public let isVideoEnabled: Bool

        public var cameraFacingMode: MediaOptions.CameraFacingMode
        public var frameRate: MediaOptions.FrameRate
        public var frameHeight: Double
        public var audioEchoCancelation: MediaOptions.AudioEchoCancellation

        public init(
            audioEnabled: Bool,
            videoEnabled: Bool,
            cameraFacingMode: PhenixCore.MediaOptions.CameraFacingMode,
            frameRate: PhenixCore.MediaOptions.FrameRate,
            frameHeight: Double = 360,
            audioEchoCancelation: PhenixCore.MediaOptions.AudioEchoCancellation
        ) {
            self.cameraFacingMode = cameraFacingMode
            self.frameRate = frameRate
            self.frameHeight = frameHeight
            self.isAudioEnabled = audioEnabled
            self.isVideoEnabled = videoEnabled
            self.audioEchoCancelation = audioEchoCancelation
        }

        func makeOptions() -> PhenixUserMediaOptions {
            let options = PhenixUserMediaOptions()

            options.video.enabled = isVideoEnabled
            options.video.capabilityConstraints[PhenixDeviceCapability.facingMode.rawValue] = [
                PhenixDeviceConstraint.initWith(cameraFacingMode.value)
            ]
            options.video.capabilityConstraints[PhenixDeviceCapability.height.rawValue] = [
                PhenixDeviceConstraint.initWith(frameHeight)
            ]

            options.video.capabilityConstraints[PhenixDeviceCapability.frameRate.rawValue] = [
                PhenixDeviceConstraint.initWith(frameRate.rawValue)
            ]

            options.audio.enabled = isAudioEnabled
            options.audio.capabilityConstraints[PhenixDeviceCapability.audioEchoCancelationMode.rawValue] = [
                PhenixDeviceConstraint.initWith(audioEchoCancelation.value)
            ]

            return options
        }
    }
}

public extension PhenixCore.MediaConfiguration {
    static let `default` = PhenixCore.MediaConfiguration(
        audioEnabled: true,
        videoEnabled: true,
        cameraFacingMode: .front,
        frameRate: .fps15,
        frameHeight: 360,
        audioEchoCancelation: .on
    )
}

extension PhenixCore.MediaConfiguration: CustomStringConvertible {
    public var description: String {
        """
        MediaConfiguration(\
        isAudioEnabled: \(isAudioEnabled), \
        isVideoEnabled: \(isVideoEnabled), \
        cameraFacingMode: \(cameraFacingMode), \
        frameRate: \(frameRate.rawValue), \
        frameHeight: \(frameHeight), \
        audioEchoCancelation: \(audioEchoCancelation)\
        )
        """
    }
}
