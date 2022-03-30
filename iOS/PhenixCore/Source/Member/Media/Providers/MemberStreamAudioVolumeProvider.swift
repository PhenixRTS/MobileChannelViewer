//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

final class MemberStreamAudioVolumeProvider {
    private static let logger = OSLog(identifier: "MemberStreamAudioVolumeProvider")

    private static let minimumDecibel: Double = -80.0
    private static let maximumDecibel: Double = 0.0
    private static let minimumNormalizedSampleAudio: Double = 0.000_000_01

    private let queue: DispatchQueue
    private let renderer: PhenixRenderer
    private let audioTracks: [PhenixMediaStreamTrack]
    private let associatedMemberID: String

    private var audioProcessPipelineCancellable: AnyCancellable?

    private let audioVolumeQueue: DispatchQueue

    private lazy var audioProcessPipelineSubject: PassthroughSubject<CMSampleBuffer, Never> = {
        let pipeline = PassthroughSubject<CMSampleBuffer, Never>()

        audioProcessPipelineCancellable = pipeline
            .receive(on: audioVolumeQueue)
            .throttle(for: .milliseconds(200), scheduler: audioVolumeQueue, latest: true)
            .compactMap { [weak self] sampleBuffer in
                self?.calculateAverageValue(of: sampleBuffer)
            }
            .removeDuplicates()
            .compactMap { [weak self] averageSampleBufferValue in
                self?.calculateVolume(from: averageSampleBufferValue)
            }
            .receive(on: queue)
            .sink { [weak self] decibel in
                self?.onAudioVolumeProvided?(decibel)
            }

        return pipeline
    }()

    var onAudioVolumeProvided: ((Double) -> Void)?

    init(associatedMemberID: String, renderer: PhenixRenderer, audioTracks: [PhenixMediaStreamTrack], queue: DispatchQueue) {
        self.queue = queue
        self.renderer = renderer
        self.audioTracks = audioTracks
        self.associatedMemberID = associatedMemberID

        self.audioVolumeQueue = DispatchQueue(
            label: "Phenix.Core.AudioVolumeProvider",
            qos: .userInitiated,
            target: queue
        )
    }

    func observeAudioVolume() {
        os_log(.debug, log: Self.logger, "%{private}s, Observe audio volume changes", associatedMemberID)

        for streamTrack in audioTracks {
            renderer.setFrameReadyCallback(streamTrack) { [weak self] notification in
                self?.didReceiveFrame(notification)
            }
        }
    }

    func stopObservingAudioVolume() {
        for streamTrack in audioTracks {
            renderer.setFrameReadyCallback(streamTrack, nil)
        }
    }

    // MARK: - Private methods

    private func calculateAverageValue(of sampleBuffer: CMSampleBuffer) -> Double? {
        guard let dataBuffer = CMSampleBufferGetDataBuffer(sampleBuffer) else {
            return nil
        }

        var totalLength = 0
        var lengthAtOffset = 0
        var dataPointer: UnsafeMutablePointer<Int8>?

        let status = CMBlockBufferGetDataPointer(
            dataBuffer,
            atOffset: 0,
            lengthAtOffsetOut: &lengthAtOffset,
            totalLengthOut: &totalLength,
            dataPointerOut: &dataPointer
        )

        guard status == kCMBlockBufferNoErr, lengthAtOffset == totalLength else {
            return nil
        }

        guard let formatDescription: CMAudioFormatDescription = CMSampleBufferGetFormatDescription(sampleBuffer) else {
            return nil
        }

        guard let audioStreamBasicDescription = CMAudioFormatDescriptionGetStreamBasicDescription(formatDescription) else {
            return nil
        }

        guard (audioStreamBasicDescription.pointee.mFormatFlags & kAudioFormatFlagIsSignedInteger) == kAudioFormatFlagIsSignedInteger else {
            assertionFailure("FormatFlags is not a signed integer")
            return nil
        }

        guard audioStreamBasicDescription.pointee.mBitsPerChannel == 16 else {
            assertionFailure("Bits per channel is not equal to 16")
            return nil
        }

        guard let dataPointer = dataPointer else {
            return nil
        }

        let samples: [Int16] = convertSamples(data: dataPointer, length: totalLength)

        let averageValue = samples
            .map { abs(Int($0)) }
            .average()

        return averageValue
    }

    private func convertSamples<T>(data: UnsafePointer<Int8>, length: Int) -> [T] {
        data.withMemoryRebound(to: T.self, capacity: length / MemoryLayout<T>.stride) { pointer -> [T] in
            let buffer = UnsafeBufferPointer(start: pointer, count: length / MemoryLayout<T>.stride)
            return Array(buffer)
        }
    }

    private func calculateVolume(from sampleAudio: Double) -> Double {
        // Calculate the mean value of the absolute values
        let normalizedSampleAudio = sampleAudio / Double(Int16.max)

        // Calculate the dB power
        let decibel: Double = {
            if normalizedSampleAudio > Self.minimumNormalizedSampleAudio {
                return 20 * log10(normalizedSampleAudio)
            } else {
                return Self.minimumDecibel
            }
        }()

        return decibel
    }

    // MARK: - Observable callbacks
    private func didReceiveFrame(_ notification: PhenixFrameNotification?) {
        notification?.read { [weak self] sampleBuffer in
            guard let sampleBuffer = sampleBuffer else {
                return
            }

            self?.audioProcessPipelineSubject.send(sampleBuffer)
        }
    }
}

// MARK: - CustomStringConvertible
extension MemberStreamAudioVolumeProvider: CustomStringConvertible {
    var description: String {
        "MemberStreamAudioVolumeProvider(alias: \(String(describing: associatedMemberID)))"
    }
}

// MARK: - Disposable
extension MemberStreamAudioVolumeProvider: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose", associatedMemberID)

        audioProcessPipelineCancellable = nil
        stopObservingAudioVolume()
        onAudioVolumeProvided = nil
    }
}

// MARK: - Helper methods for the Sequence.
fileprivate extension Sequence where Element: BinaryFloatingPoint {
    func average() -> Double {
        var i: Double = 0
        var total: Double = 0

        for value in self {
            total = total + Double(value)
            i += 1
        }

        return total / i
    }
}

fileprivate extension Sequence where Element: BinaryInteger {
    func average() -> Double {
        var i: Double = 0
        var total: Double = 0

        for value in self {
            total = total + Double(value)
            i += 1
        }

        return total / i
    }
}
