//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

extension PhenixCore {
    final class PublishingChannel {
        private static let logger = OSLog(identifier: "PublishingChannel")

        private var roomService: PhenixRoomService?
        private var mediaPublisher: PhenixExpressPublisher?
        private var configuration: Channel.Configuration

        private(set) var isLeaving: Bool

        var id: String? { roomService?.getObservableActiveRoom().getValue().getId() }
        var alias: String { configuration.alias }

        init(configuration: Channel.Configuration) {
            self.configuration = configuration
            self.isLeaving = false
        }

        /// Provides a media publisher to the Channel instance, where it is kept alive.
        /// - Parameter publisher: Media publisher
        func setPublisher(_ publisher: PhenixExpressPublisher, roomService: PhenixRoomService) {
            os_log(.debug, log: Self.logger, "%{private}s, Set publisher", alias)

            self.mediaPublisher = publisher
            self.roomService = roomService
        }

        func stopPublishing() {
            os_log(.debug, log: Self.logger, "%{private}s, Stop publishing", alias)

            mediaPublisher?.stop()
            mediaPublisher = nil
        }

        func setPublisherAudio(enabled: Bool) {
            os_log(.debug, log: Self.logger, "%{public}s, Set publisher audio: %{public}s", alias, enabled.description)

            if enabled {
                mediaPublisher?.enableAudio()
            } else {
                mediaPublisher?.disableAudio()
            }
        }

        func setPublisherVideo(enabled: Bool) {
            os_log(.debug, log: Self.logger, "%{public}s, Set publisher video: %{public}s", alias, enabled.description)

            if enabled {
                mediaPublisher?.enableVideo()
            } else {
                mediaPublisher?.disableVideo()
            }
        }

        func leave(then handler: @escaping () -> Void) {
            guard isLeaving == false else {
                os_log(.debug, log: Self.logger, "%{private}s, Publishing channel leaving already in progress", alias)
                return
            }

            os_log(.debug, log: Self.logger, "%{private}s, Leave publishing channel", alias)

            isLeaving = true

            stopPublishing()
            roomService?.leaveRoom { _, _ in
                handler()
            }
        }
    }
}
