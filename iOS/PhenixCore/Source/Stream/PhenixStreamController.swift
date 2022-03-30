//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

extension PhenixCore {
    final class StreamController {
        private static let logger = OSLog(identifier: "StreamController")

        private let queue: DispatchQueue
        private let pcastExpress: PhenixPCastExpress

        // MARK: - Subjects
        private var eventSubject = PassthroughSubject<Stream.Event, Never>()
        private var streamsSubject = CurrentValueSubject<[Stream], Never>([])

        // MARK: - Publishers
        lazy var eventPublisher = eventSubject.eraseToAnyPublisher()
        lazy var streamsPublisher = streamsSubject.eraseToAnyPublisher()

        // MARK: - Other
        var streams: [Stream] { streamsSubject.value }

        init(pcastExpress: PhenixPCastExpress, queue: DispatchQueue = .main) {
            self.queue = queue
            self.pcastExpress = pcastExpress
        }

        func joinToStream(configuration: Stream.Configuration) {
            os_log(.debug, log: Self.logger, "%{private}s, Join to stream", configuration.id)

            eventSubject.send(.streamJoining(id: configuration.id))

            let stream = Stream(configuration: configuration, queue: queue, publisherQueue: .main)
            let options = PCastOptionsFactory.makeSubscriberOptions(configuration: configuration)

            streamsSubject.value.append(stream)
            stream.prepareToSubscribe()

            pcastExpress.subscribe(options) { [weak self, weak stream] status, subscriber, _ in
                self?.queue.async {
                    guard let self = self, let stream = stream else {
                        return
                    }

                    stream.handleSubscription(status: status, subscriber: subscriber)

                    switch status {
                    case .ok:
                        self.eventSubject.send(.streamJoined(id: configuration.id))

                    default:
                        self.eventSubject.send(.streamJoiningFailed(id: configuration.id, error: .invalid(status)))
                    }
                }
            }
        }

        func selectStream(id: String, isSelected: Bool) {
            stream(withID: id)?.setSelection(enabled: isSelected)
        }

        func setAudioEnabled(id: String, enabled: Bool) {
            stream(withID: id)?.setAudio(enabled: enabled)
        }

        func setBandwidthLimitation(_ bandwidth: UInt64, id: String) {
            stream(withID: id)?.setBandwidthLimitation(bandwidth)
        }

        func removeBandwidthLimitation(id: String) {
            stream(withID: id)?.removeBandwidthLimitation()
        }

        func renderVideo(id: String, layer: CALayer?) {
            stream(withID: id)?.renderVideo(layer: layer)
        }

        func renderThumbnailVideo(id: String, layer: CALayer?) {
            stream(withID: id)?.renderThumbnailVideo(layer: layer)
        }

        func leave(id: String) {
            if let index = streams.firstIndex(where: { $0.id == id }) {
                os_log(.debug, log: Self.logger, "%{private}s, Leave stream", id)
                streamsSubject.value
                    .remove(at: index)
                    .dispose()
            }
        }

        // MARK: - Private methods

        func stream(withID id: String) -> PhenixCore.Stream? {
            streams.first { $0.id == id }
        }
    }
}
