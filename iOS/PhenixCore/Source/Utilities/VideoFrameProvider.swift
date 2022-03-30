//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import CoreMedia
import Foundation
import PhenixSdk
import os.log

extension PhenixCore {
    class VideoFrameProvider {
        private static let logger = OSLog(identifier: "VideoFrameProvider")

        private let queue: DispatchQueue
        private let renderer: PhenixRenderer
        private let videoTracks: [PhenixMediaStreamTrack]

        var processesFrames: Bool
        var onFrameReceived: ((CMSampleBuffer) -> Void)?

        init(renderer: PhenixRenderer, videoTracks: [PhenixMediaStreamTrack], queue: DispatchQueue) {
            self.queue = queue
            self.renderer = renderer
            self.videoTracks = videoTracks
            self.processesFrames = true
        }

        func observeFrames() {
            for streamTrack in videoTracks {
                renderer.setFrameReadyCallback(streamTrack) { [weak self] notification in
                    self?.didReceiveFrame(notification)
                }
                renderer.setLastVideoFrameRenderedReceivedCallback { [weak self] renderer, pixelBuffer in
                    self?.didReceiveLastVideoFrame(renderer, pixelBuffer)
                }
            }
        }

        func stopObservingFrames() {
            for streamTrack in videoTracks {
                renderer.setFrameReadyCallback(streamTrack, nil)
            }
        }

        func requestLastVideoFrameRendered() {
            renderer.requestLastVideoFrameRendered()
        }

        // MARK: - Private methods

        private func didReceiveFrame(_ frameNotification: PhenixFrameNotification?) {
            guard processesFrames else {
                return
            }

            frameNotification?.read { [weak self] sampleBuffer in
                guard let sampleBuffer = sampleBuffer else {
                    return
                }

                self?.modify(sampleBuffer)
                self?.queue.async {
                    self?.onFrameReceived?(sampleBuffer)
                }
            }
        }

        private func didReceiveLastVideoFrame(_ renderer: PhenixRenderer?, _ nativeVideoFrame: CVPixelBuffer?) {
            guard processesFrames else {
                return
            }

            guard let nativeVideoFrame = nativeVideoFrame else {
                return
            }

            guard let sampleBuffer = nativeVideoFrame.createSampleBufferFrame() else {
                return
            }

            modify(sampleBuffer)
            queue.async { [ weak self] in
                self?.onFrameReceived?(sampleBuffer)
            }
        }

        private func modify(_ sampleBuffer: CMSampleBuffer) {
            if let attachmentArray = CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, createIfNecessary: true) {
                let count = CFArrayGetCount(attachmentArray)
                for index in 0..<count {
                    if let unsafeRawPointer = CFArrayGetValueAtIndex(attachmentArray, index) {
                        let attachments = unsafeBitCast(unsafeRawPointer, to: CFMutableDictionary.self)
                        // Need to set the sample buffer to display frame immediately
                        // and ignore whatever timestamps are included.
                        // Without this, iOS 14 will not render the video frames.
                        CFDictionarySetValue(attachments,
                                             unsafeBitCast(kCMSampleAttachmentKey_DisplayImmediately, to: UnsafeRawPointer.self),
                                             unsafeBitCast(kCFBooleanTrue, to: UnsafeRawPointer.self))
                    }
                }
            }
        }
    }
}

// MARK: - Disposable
extension PhenixCore.VideoFrameProvider: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "Dispose")

        stopObservingFrames()
        onFrameReceived = nil
    }
}
