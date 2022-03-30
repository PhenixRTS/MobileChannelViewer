//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import os.log
import PhenixSdk

extension PhenixCore {
    class CoreController {
        private static let logger = OSLog(identifier: "PhenixCoreController")

        private let queue: DispatchQueue
        private let publisherQueue: DispatchQueue

        // MARK: - Subjects
        private let eventSubject = PassthroughSubject<PhenixCore.Event, PhenixCore.Error>()

        // MARK: - Express
        private var channelExpress: PhenixChannelExpress!
        private var pcastExpress: PhenixPCastExpress { channelExpress.pcastExpress }
        private var roomExpress: PhenixRoomExpress { channelExpress.roomExpress }

        // MARK: - Controllers
        // MARK: Room
        private var roomController: RoomController!
        private var roomCancellable: AnyCancellable?

        // MARK: Channels
        private var channelController: ChannelController!
        private var channelCancellable: AnyCancellable?

        // MARK: Streams
        private var streamController: StreamController!
        private var streamCancellable: AnyCancellable?

        // MARK: Messages
        private var messageController: MessageController!
        private var messageCancellable: AnyCancellable?

        // MARK: TimeShift
        private var timeShiftController: TimeShiftController!
        private var timeShiftCancellable: AnyCancellable?

        // MARK: Device media
        private var mediaController: MediaController?
        private var mediaCancellable: AnyCancellable?
        private var mediaInterruptionService: PhenixMediaInterruptionService?
        private var mediaInterruptionCancellable: AnyCancellable?

        // MARK: - Core state
        private(set) var isInitialized = false

        // MARK: - Configuration
        let configuration: Configuration

        // MARK: - Publishers

        /// An event publisher which informs about various **PhenixCore** events, for example, when media is initialized or a member starts to publish to the room.
        lazy var eventPublisher = eventSubject.eraseToAnyPublisher()
        lazy var membersPublisher = roomController.membersPublisher.eraseToAnyPublisher()
        lazy var streamsPublisher = streamController.streamsPublisher.eraseToAnyPublisher()
        lazy var messagesPublisher = messageController.messagesPublisher.eraseToAnyPublisher()
        lazy var channelsPublisher = channelController.channelsPublisher.eraseToAnyPublisher()
        lazy var memberCountPublisher = roomController.memberCountPublisher.eraseToAnyPublisher()

        init(configuration: Configuration, queue: DispatchQueue, publisherQueue: DispatchQueue) {
            self.queue = queue
            self.configuration = configuration
            self.publisherQueue = publisherQueue
        }

        /// Setup all of the controllers.
        func setup() {
            let setupProvider = PhenixCoreSetup(configuration: configuration) { [weak self] description in
                self?.isInitialized = false
                self?.eventSubject.send(completion: .failure(.unrecoverableError(description: description)))
            }

            channelExpress = setupProvider.setupChannelExpress()

            setupRoomController()
            setupStreamController()
            setupChannelController()
            setupMessageController()
            setupTimeShiftController()

            isInitialized = true
            eventSubject.send(.coreInitialized)
        }

        // MARK: - Channel

        func createChannel(configuration: Channel.Configuration) {
            whenCoreInitialized {
                channelController.createChannel(configuration: configuration)
            }
        }

        func joinToChannel(configuration: Channel.Configuration) {
            whenCoreInitialized {
                channelController.joinToChannel(configuration: configuration)
            }
        }

        func publishToChannel(configuration: Channel.Configuration) {
            whenCoreInitialized {
                whenLocalMediaStreamEnabled { userMediaStream in
                    channelController.publishToChannel(configuration: configuration, userMediaStream: userMediaStream)
                }
            }
        }

        func selectChannel(alias: String, isSelected: Bool) {
            whenCoreInitialized {
                channelController.selectChannel(alias: alias, isSelected: isSelected)
            }
        }

        func stopPublishingToChannel() {
            whenCoreInitialized {
                channelController.stopPublishing()
            }
        }

        // MARK: - Streams

        func joinToStream(configuration: Stream.Configuration) {
            whenCoreInitialized {
                streamController.joinToStream(configuration: configuration)
            }
        }

        func selectStream(id: String, isSelected: Bool) {
            whenCoreInitialized {
                streamController.selectStream(id: id, isSelected: isSelected)
            }
        }

        // MARK: - Room

        func createRoom(configuration: Room.Configuration) {
            whenCoreInitialized {
                roomController.createRoom(configuration: configuration)
            }
        }

        func joinToRoom(configuration: Room.Configuration) {
            whenCoreInitialized {
                roomController.joinToRoom(configuration: configuration)
            }
        }

        func publishToRoom(configuration: Room.Configuration) {
            whenCoreInitialized {
                whenLocalMediaStreamEnabled { userMediaStream in
                    roomController.publishToRoom(configuration: configuration, userMediaStream: userMediaStream)
                }
            }
        }

        func subscribeToRoomMembers() {
            whenCoreInitialized {
                roomController.subscribeToRoomMembers()
            }
        }

        func stopPublishingToRoom() {
            whenCoreInitialized {
                roomController.stopPublishing()
            }
        }

        // MARK: - Member

        func selectMember(id: Member.ID, isSelected: Bool) {
            whenCoreInitialized {
                roomController.selectMember(id: id, isSelected: isSelected)
            }
        }

        func updateMember(id: Member.ID, role: Member.Role? = nil, state: Member.State? = nil, name: String? = nil) {
            whenCoreInitialized {
                roomController.updateMember(id: id, role: role, state: state, name: name)
            }
        }

        // MARK: - Message

        func subscribeToMessages(alias: String, configuration: Message.Configuration) {
            whenCoreInitialized {
                let roomService: PhenixRoomService? = {
                    roomController.roomService(withAlias: alias) ?? channelController.roomService(withAlias: alias)
                }()

                guard let roomService = roomService else {
                    // TODO: Provide an error event back.
                    return
                }

                messageController.subscribeForMessages(
                    alias: alias,
                    roomService: roomService,
                    configuration: configuration
                )
            }
        }

        func unsubscribeMessages(alias: String, mimeType: String? = nil) {
            whenCoreInitialized {
                if let mimeType = mimeType {
                    messageController.unsubscribeMessages(alias: alias, mimeType: mimeType)
                } else {
                    messageController.unsubscribeAllMessages(alias: alias)
                }
            }
        }

        func sendMessage(alias: String, message: String, mimeType: String) {
            whenCoreInitialized {
                messageController.sendMessage(alias: alias, message: message, mimeType: mimeType)
            }
        }

        // MARK: - Disposing

        func leave(alias: String) {
            whenCoreInitialized {
                roomController.leaveRoom(alias: alias)
                streamController.leave(id: alias)
                channelController.leaveChannel(alias: alias)
                messageController.unsubscribeAllMessages(alias: alias)
                timeShiftController.removeTimeShift(alias: alias)
            }
        }

        // MARK: - TimeShift

        func createTimeShift(alias: String, on pointInTime: TimeShift.PointInTime) {
            whenCoreInitialized {
                let timeShifter: PhenixTimeShifter? = {
                    channelController.channel(withAlias: alias) ?? streamController.stream(withID: alias)
                }()

                guard let timeShifter = timeShifter else {
                    eventSubject.send(.timeShiftCreationFailed)
                    return
                }

                guard let renderer = timeShifter.renderer else {
                    eventSubject.send(.timeShiftCreationFailed)
                    return
                }

                let timeShift = timeShiftController.createTimeShift(alias: alias, on: pointInTime, with: renderer)
                timeShifter.subscribe(timeShift)
            }
        }

        func playTimeShift(alias: String) {
            whenCoreInitialized {
                timeShiftController.playTimeShift(alias: alias)
            }
        }

        func playTimeShift(alias: String, loop duration: TimeInterval) {
            whenCoreInitialized {
                timeShiftController.playTimeShift(alias: alias, loop: duration)
            }
        }

        func seekTimeShift(alias: String, offset: TimeInterval) {
            whenCoreInitialized {
                timeShiftController.seekTimeShift(alias: alias, offset: offset)
            }
        }

        func pauseTimeShift(alias: String) {
            whenCoreInitialized {
                timeShiftController.pauseTimeShift(alias: alias)
            }
        }

        func stopTimeShift(alias: String) {
            whenCoreInitialized {
                timeShiftController.stopTimeShift(alias: alias)
            }
        }

        // MARK: - Bandwidth

        func setBandwidthLimitation(alias: String, bandwidth: UInt64) {
            whenCoreInitialized {
                streamController.setBandwidthLimitation(bandwidth, id: alias)
                channelController.setBandwidthLimitation(bandwidth, alias: alias)
                timeShiftController.setBandwidthLimitation(bandwidth, alias: alias)
            }
        }

        func removeBandwidthLimitation(alias: String) {
            whenCoreInitialized {
                streamController.removeBandwidthLimitation(id: alias)
                channelController.removeBandwidthLimitation(alias: alias)
                timeShiftController.removeBandwidthLimitation(alias: alias)
            }
        }

        // MARK: - Remote Media

        func renderVideo(alias: String, layer: CALayer?) {
            whenCoreInitialized {
                roomController.renderVideo(alias: alias, layer: layer)
                streamController.renderVideo(id: alias, layer: layer)
                channelController.renderVideo(alias: alias, layer: layer)
            }
        }

        func renderThumbnailVideo(alias: String, layer: CALayer?) {
            whenCoreInitialized {
                roomController.renderThumbnailVideo(alias: alias, layer: layer)
                streamController.renderThumbnailVideo(id: alias, layer: layer)
                channelController.renderThumbnailVideo(alias: alias, layer: layer)
            }
        }

        func setAudioEnabled(alias: String, enabled: Bool) {
            whenCoreInitialized {
                roomController.setAudioEnabled(memberID: alias, enabled: enabled)
                streamController.setAudioEnabled(id: alias, enabled: enabled)
                channelController.setAudioEnabled(alias: alias, enabled: enabled)
            }
        }

        func setVideoEnabled(alias: String, enabled: Bool) {
            whenCoreInitialized {
                roomController.setVideoEnabled(memberID: alias, enabled: enabled)
            }
        }

        // MARK: - Local Media

        func previewOnSurface(layer: CALayer?) {
            whenCoreInitialized {
                whenLocalMediaEnabled {
                    mediaController?.previewOnSurface(layer: layer)
                }
            }
        }

        func previewOnImage(layer: CALayer?) {
            whenCoreInitialized {
                whenLocalMediaEnabled {
                    mediaController?.previewOnImage(layer: layer)
                }
            }
        }

        func setSelfAudioEnabled(enabled: Bool) {
            whenCoreInitialized {
                mediaController?.setAudioEnabled(enabled: enabled)
                roomController.setSelfAudioEnabled(enabled: enabled)
            }
        }

        func setSelfVideoEnabled(enabled: Bool) {
            whenCoreInitialized {
                mediaController?.setVideoEnabled(enabled: enabled)
                roomController.setSelfVideoEnabled(enabled: enabled)
            }
        }

        func setLocalMedia(enabled: Bool, configuration: PhenixCore.MediaConfiguration = .default) {
            whenCoreInitialized {
                if enabled {
                    setupMediaController(configuration: configuration)
                } else {
                    disposeMediaController()
                }
            }
        }

        func updateLocalMedia(_ configuration: PhenixCore.MediaConfiguration) {
            whenCoreInitialized {
                whenLocalMediaStreamEnabled { _ in
                    mediaController?.update(configuration)
                }
            }
        }

        func flipCamera() {
            whenCoreInitialized {
                whenLocalMediaStreamEnabled { _ in
                    mediaController?.flipCamera()
                }
            }
        }

        // MARK: - Debug

        func collectLogs(completion: @escaping (String?) -> Void) {
            whenCoreInitialized {
                pcastExpress.pcast.collectLogMessages { _, status, messages in
                    DispatchQueue.main.async {
                        guard status == .ok, let messages = messages else {
                            completion(nil)
                            return
                        }

                        completion(messages)
                    }
                }
            }
        }

        // MARK: - Private methods

        // MARK: Setups

        private func setupRoomController() {
            let roomController = RoomController(roomExpress: roomExpress, queue: queue)
            self.roomController = roomController
            roomController.mediaDelegate = self

            roomCancellable = roomController.eventPublisher
                .map { PhenixCore.Event.room($0) }
                .sink { [weak self] event in
                    self?.eventSubject.send(event)
                }
        }

        private func setupMediaController(configuration: MediaConfiguration) {
            let mediaController = MediaController(pcastExpress: pcastExpress, queue: queue)
            self.mediaController = mediaController

            mediaCancellable = mediaController.eventPublisher
                .map { PhenixCore.Event.media($0) }
                .sink { [weak self] event in
                    self?.eventSubject.send(event)
                }

            mediaController.start(configuration: configuration)
            setupMediaInterruptionOnBackgrounding()
        }

        private func disposeMediaController() {
            mediaController?.dispose()
            mediaController = nil

            mediaInterruptionCancellable = nil
            mediaInterruptionService = nil
        }

        private func setupMediaInterruptionOnBackgrounding() {
            let service = PhenixMediaInterruptionService { [weak self] in
                self?.mediaController?.isVideoEnabled() ?? false
            }

            mediaInterruptionService = service
            mediaInterruptionCancellable = service.suggestedVideoStatePublisher
                .sink { [weak self] state in
                    self?.setSelfVideoEnabled(enabled: state == .enabled)
                }

            service.start()
        }

        private func setupChannelController() {
            channelController = ChannelController(channelExpress: channelExpress, queue: queue)
            channelCancellable = channelController.eventPublisher
                .map { PhenixCore.Event.channel($0) }
                .sink { [weak self] event in
                    self?.eventSubject.send(event)
                }
        }

        private func setupStreamController() {
            streamController = StreamController(pcastExpress: pcastExpress, queue: queue)
            streamCancellable = streamController.eventPublisher
                .map { PhenixCore.Event.stream($0) }
                .sink { [weak self] event in
                    self?.eventSubject.send(event)
                }
        }

        private func setupMessageController() {
            messageController = MessageController(queue: queue)
            messageCancellable = messageController.eventPublisher
                .map { PhenixCore.Event.message($0) }
                .sink { [weak self] event in
                    self?.eventSubject.send(event)
                }
        }

        private func setupTimeShiftController() {
            timeShiftController = TimeShiftController(queue: queue)
            timeShiftCancellable = timeShiftController.eventPublisher
                .map { PhenixCore.Event.timeShift($0) }
                .sink { [weak self] event in
                    self?.eventSubject.send(event)
                }
        }

        // MARK: State checks

        private func whenCoreInitialized(then: () -> Void) {
            guard isInitialized == true else {
                eventSubject.send(.coreNotInitialized)
                return
            }

            then()
        }

        /// Verify that the local media is created before continuing with closure.
        /// - Parameter then: Closure which is executed only if the local media exists.
        private func whenLocalMediaEnabled(then: () -> Void) {
            guard mediaController != nil else {
                eventSubject.send(.media(.mediaNotInitialized))
                return
            }

            then()
        }

        /// Verify that the local media is created and the local media stream is available before continuing with closure.
        /// - Parameter then: Closure which is executed only if the local media exists and media stream is available.
        private func whenLocalMediaStreamEnabled(then: (PhenixUserMediaStream) -> Void) {
            guard let mediaController = mediaController else {
                eventSubject.send(.media(.mediaNotInitialized))
                return
            }

            guard let userMediaStream = mediaController.userMediaStream else {
                eventSubject.send(.media(.mediaNotInitialized))
                return
            }

            then(userMediaStream)
        }
    }
}


// MARK: - UserMediaStreamProvider
extension PhenixCore.CoreController: LocalMediaProvider {
    func getLocalMediaRenderer() -> PhenixRenderer? {
        dispatchPrecondition(condition: .onQueue(queue))

        return mediaController?.renderer
    }

    func getLocalMediaAudioTracks() -> [PhenixMediaStreamTrack] {
        dispatchPrecondition(condition: .onQueue(queue))

        return mediaController?.userMediaStream?.mediaStream.getAudioTracks() ?? []
    }
}
