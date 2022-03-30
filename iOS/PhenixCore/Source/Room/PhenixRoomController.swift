//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

// swiftlint:disable file_length

import Combine
import Foundation
import os.log
import PhenixSdk

extension PhenixCore {
    final class RoomController {
        private static let logger = OSLog(identifier: "RoomController")

        // MARK: - General
        private let queue: DispatchQueue
        private let publisherQueue: DispatchQueue
        private let roomExpress: PhenixRoomExpress

        // MARK: - Room
        private var room: Room?

        /// Represents that we are leaving the room currently.
        private var isLeaving: Bool

        // MARK: - Members
        private var memberControllers: [Member.ID: MemberController]

        /// Overrides `Room.Configuration.joinsSilently` property and allows all new member streams to be subscribed.
        private var allowsMemberSubscription: Bool

        // MARK: - Combine
        private var roomChangeCancellable: AnyCancellable?
        private var roomMemberCancellable: AnyCancellable?
        private var roomMemberCountCancellable: AnyCancellable?

        // MARK: - Subjects
        private var eventSubject = PassthroughSubject<Room.Event, Never>()
        private var membersSubject = CurrentValueSubject<[Member], Never>([])
        private var memberCountSubject = CurrentValueSubject<Int, Never>(0)

        // MARK: - Publishers
        lazy var eventPublisher: AnyPublisher<Room.Event, Never> = eventSubject.eraseToAnyPublisher()
        lazy var membersPublisher: AnyPublisher<[Member], Never> = membersSubject.eraseToAnyPublisher()
        lazy var memberCountPublisher: AnyPublisher<Int, Never> = memberCountSubject.eraseToAnyPublisher()

        // MARK: - Other
        var members: [Member] { membersSubject.value }
        var selfMember: Member? { members.selfMember() }

        weak var mediaDelegate: LocalMediaProvider?

        init(roomExpress: PhenixRoomExpress, queue: DispatchQueue = .main, publisherQueue: DispatchQueue = .main) {
            self.queue = queue
            self.isLeaving = false
            self.roomExpress = roomExpress
            self.publisherQueue = publisherQueue
            self.memberControllers = [:]
            self.allowsMemberSubscription = false
        }

        func createRoom(configuration: Room.Configuration) {
            os_log(.debug, log: Self.logger, "%{private}s, Creating room", configuration.alias)

            eventSubject.send(.roomCreating(alias: configuration.alias))

            let options = RoomOptionsFactory.makeCreateRoomOptions(configuration: configuration)
            roomExpress.createRoom(options) { [weak self] status, _ in
                self?.queue.async {
                    guard let self = self else {
                        return
                    }

                    os_log(
                        .debug,
                        log: Self.logger,
                        "%{private}s, Created room with status: %{public}s",
                        configuration.alias,
                        status.description
                    )

                    switch status {
                    case .ok:
                        self.eventSubject.send(.roomCreated(alias: configuration.alias))

                    default:
                        self.eventSubject.send(.roomCreationFailed(alias: configuration.alias, error: .invalid(status)))
                    }
                }
            }
        }

        func joinToRoom(configuration: Room.Configuration) {
            os_log(.debug, log: Self.logger, "%{private}s, Joining to room", configuration.alias)

            eventSubject.send(.roomJoining(alias: configuration.alias))

            guard room == nil else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Joining to room failed, already joined",
                    configuration.alias
                )

                eventSubject.send(.roomJoiningFailed(alias: configuration.alias, error: .roomAlreadyJoined))
                return
            }

            let options = RoomOptionsFactory.makeJoinToRoomOptions(configuration: configuration)
            roomExpress.joinRoom(options) { [weak self] status, roomService in
                self?.queue.async {
                    guard let self = self else {
                        return
                    }

                    os_log(
                        .debug,
                        log: Self.logger,
                        "%{private}s, Joined to room with status: %{public}s",
                        configuration.alias,
                        status.description
                    )

                    switch status {
                    case .ok:
                        guard let roomService = roomService else {
                            os_log(
                                .debug,
                                log: Self.logger,
                                "%{private}s, Room joining failed, missing Room Service",
                                configuration.alias
                            )

                            self.eventSubject.send(
                                .roomJoiningFailed(alias: configuration.alias, error: .missingRoomService)
                            )
                            return
                        }

                        self.onRoomJoin(roomService: roomService, configuration: configuration)
                        self.eventSubject.send(.roomJoined(alias: configuration.alias))

                    default:
                        self.eventSubject.send(.roomJoiningFailed(alias: configuration.alias, error: .invalid(status)))
                    }
                }
            }
        }

        func publishToRoom(configuration: Room.Configuration, userMediaStream: PhenixUserMediaStream) {
            os_log(.debug, log: Self.logger, "%{private}s, Publishing to room")

            eventSubject.send(.roomPublishing(alias: configuration.alias))

            guard let room = room else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Room publishing failed, no room joined",
                    configuration.alias
                )

                eventSubject.send(.roomPublishingFailed(alias: configuration.alias, error: .roomNotJoined))
                return
            }

            guard room.isPublishing == false else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Room publishing failed, already publishing media",
                    configuration.alias
                )

                eventSubject.send(
                    .roomPublishingFailed(alias: configuration.alias, error: .mediaAlreadyPublishing)
                )
                return
            }

            guard isLeaving == false else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Room publishing failed, room leaving in progress",
                    configuration.alias
                )

                eventSubject.send(.roomPublishingFailed(alias: configuration.alias, error: .roomAlreadyLeaving))
                return
            }

            guard let publishToken = configuration.publishToken else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Room publishing failed, missing publish token",
                    configuration.alias
                )

                eventSubject.send(.roomPublishingFailed(alias: configuration.alias, error: .missingPublishToken))
                return
            }

            let roomOptions = RoomOptionsFactory.makeCreateRoomOptions(configuration: configuration)

            let publishOptions = PublishOptionsFactory.makePublishOptions(
                userMediaStream: userMediaStream,
                publishToken: publishToken
            )

            let publishToRoomOptions = RoomOptionsFactory.makePublishToRoomOptions(
                roomOptions: roomOptions,
                publishOptions: publishOptions,
                configuration: configuration,
                roomID: room.id
            )

            roomExpress.publish(toRoom: publishToRoomOptions) { [weak self] status, roomService, publisher in
                self?.queue.async {
                    guard let self = self else {
                        return
                    }

                    os_log(
                        .debug,
                        log: Self.logger,
                        "%{private}s, Published to room with status: %{public}s",
                        configuration.alias,
                        status.description
                    )

                    switch status {
                    case .ok:
                        guard let roomService = roomService else {
                            os_log(
                                .debug,
                                log: Self.logger,
                                "%{private}s, Room publishing failed, missing Room Service",
                                configuration.alias
                            )

                            self.eventSubject.send(
                                .roomPublishingFailed(alias: configuration.alias, error: .missingRoomService)
                            )
                            return
                        }

                        guard let publisher = publisher else {
                            os_log(
                                .debug,
                                log: Self.logger,
                                "%{private}s, Room publishing failed, missing Publisher",
                                configuration.alias
                            )

                            self.eventSubject.send(
                                .roomPublishingFailed(alias: configuration.alias, error: .missingPublisher)
                            )
                            return
                        }

                        self.onRoomPublish(roomService: roomService, publisher: publisher, configuration: configuration)
                        self.eventSubject.send(.roomPublished(alias: configuration.alias))

                    default:
                        self.eventSubject.send(
                            .roomPublishingFailed(alias: configuration.alias, error: .invalid(status))
                        )
                    }
                }
            }
        }

        /// Subscribe to current room member media streams.
        ///
        /// Without subscribing to these members, core will not receive any media or any state change callbacks.
        func subscribeToRoomMembers() {
            guard let room = room else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Subscription to room members failed, no room joined",
                    description
                )

                // FIXME: When multiple rooms will be available, provide a proper room alias.
                eventSubject.send(.roomMemberSubscriptionFailed(roomAlias: "", error: .roomNotJoined))
                return
            }

            guard isLeaving == false else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Subscription to room members failed, room leaving in progress",
                    room.alias
                )

                eventSubject.send(.roomMemberSubscriptionFailed(roomAlias: room.alias, error: .roomAlreadyLeaving))
                return
            }

            guard room.configuration.joinsSilently == true else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Subscription to room members failed, room members are prevented to join silently",
                    room.alias
                )
                return
            }

            os_log(.debug, log: Self.logger, "%{private}s, Subscribe to room members", room.alias)

            allowsMemberSubscription = true

            members.forEach(subscribeToMemberStream)
        }

        func stopPublishing() {
            room?.stopPublishing()
        }

        func leaveRoom(alias: String) {
            guard let room = room(withAlias: alias) else {
                return
            }

            guard isLeaving == false else {
                os_log(.debug, log: Self.logger, "%{private}s, Room leaving already in progress", alias)
                eventSubject.send(.roomLeavingFailed(alias: alias, error: .roomAlreadyLeaving))
                return
            }

            os_log(.debug, log: Self.logger, "%{private}s, Leave room", alias)
            eventSubject.send(.roomLeaving(alias: alias))

            isLeaving = true

            room.leave { [weak self] in
                self?.queue.async {
                    os_log(.debug, log: Self.logger, "%{private}s, Room left", alias)

                    self?.resetRoomController()
                    self?.eventSubject.send(.roomLeft(alias: alias))
                }
            }
        }

        func setSelfAudioEnabled(enabled: Bool) {
            room?.setPublisherAudio(enabled: enabled)
        }

        func setSelfVideoEnabled(enabled: Bool) {
            room?.setPublisherVideo(enabled: enabled)
        }

        func setAudioEnabled(memberID: String, enabled: Bool) {
            guard let member = member(withID: memberID) else {
                return
            }

            if member.isSelf {
                room?.setPublisherAudio(enabled: enabled)
            } else {
                os_log(.debug, log: Self.logger, "%{public}s, Can't change audio state of a remote member.", memberID)
                eventSubject.send(
                    .roomMemberMediaChangeFailed(memberID: memberID, error: .blockedRemoteMemberMediaChange)
                )
            }
        }

        func setVideoEnabled(memberID: String, enabled: Bool) {
            guard let member = member(withID: memberID) else {
                return
            }

            if member.isSelf {
                room?.setPublisherVideo(enabled: enabled)
            } else {
                os_log(.debug, log: Self.logger, "%{public}s, Can't change video state of a remote member.", memberID)
                eventSubject.send(
                    .roomMemberMediaChangeFailed(memberID: memberID, error: .blockedRemoteMemberMediaChange)
                )
            }
        }

        func renderVideo(alias: String, layer: CALayer?) {
            memberController(withID: alias)?.renderVideo(layer: layer)
        }

        func renderThumbnailVideo(alias: String, layer: CALayer?) {
            memberController(withID: alias)?.renderThumbnailVideo(layer: layer)
        }

        func roomService(withAlias alias: String) -> PhenixRoomService? {
            room(withAlias: alias)?.roomService
        }

        func selectMember(id: Member.ID, isSelected: Bool) {
            member(withID: id)?.setSelection(enabled: isSelected)
        }

        func updateMember(id: Member.ID, role: Member.Role? = nil, state: Member.State? = nil, name: String? = nil) {
            member(withID: id)?.update(role: role, state: state, name: name)
        }

        // MARK: - Private methods

        /// Sets up a Room instance.
        /// - Parameters:
        ///   - roomService: Room service for the currently joined room.
        ///   - configuration: Room configuration.
        private func onRoomJoin(roomService: PhenixRoomService, configuration: Room.Configuration) {
            let room = Room(
                roomService: roomService,
                configuration: configuration,
                queue: queue,
                publisherQueue: publisherQueue
            )

            room.start()
            subscribeToRoomMembers(room)
            subscribeToRoomMemberCount(room)

            self.room = room
        }

        /// Sets up the publisher for the room and the room service from the publisher.
        /// - Parameters:
        ///   - roomService: Room service for the currently published room.
        ///   - publisher: Publisher to the room.
        ///   - configuration: Room configuration.
        private func onRoomPublish(
            roomService: PhenixRoomService,
            publisher: PhenixExpressPublisher,
            configuration: Room.Configuration
        ) {
            guard let room = room else {
                return
            }

            room.setPublisher(publisher, publisherRoomService: roomService)
            room.configuration = configuration
        }

        private func subscribeToRoomMemberCount(_ room: Room) {
            roomMemberCountCancellable = room.sizePublisher
                .removeDuplicates()
                .receive(on: queue)
                .sink { [weak self] size in
                    self?.memberCountSubject.send(size)
                }
        }

        private func subscribeToRoomMembers(_ room: Room) {
            // swiftlint:disable:next trailing_closure
            roomMemberCancellable = room.membersPublisher
                .handleEvents(receiveOutput: { [weak self] members in
                    guard let self = self else { return }

                    let changes = members.difference(from: self.members).inferringMoves()

                    os_log(
                        .debug,
                        log: Self.logger,
                        "%{private}s, Member list changed: %{private}s",
                        self.description,
                        members.description
                    )

                    for change in changes {
                        switch change {
                        case .insert(_, let member, let association):
                            // If the associated index isn't nil, that means
                            // that the insert is associated with a removal,
                            // and we can treat it as a move.
                            if association == nil {
                                self.process(added: member)
                            }
                        case .remove(_, let member, let association):
                            // We'll only process removals if the associated
                            // index is nil, since otherwise we will already
                            // have handled that operation as a move above.
                            if association == nil {
                                self.process(removed: member)
                            }
                        }
                    }
                })
                .sink { [weak self] members in
                    self?.membersSubject.send(members)
                }
        }

        private func room(withAlias alias: String) -> Room? {
            if alias == room?.alias {
                return room
            } else {
                return nil
            }
        }

        // MARK: - Member methods

        private func subscribeToMemberStream(_ member: Member) {
            // Drop the existing member controller
            unsubscribeFromMemberStream(member)

            guard let room = room else {
                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Subscription to room members failed, no room joined",
                    description
                )

                return
            }

            os_log(.debug, log: Self.logger, "Create MemberController for: %{public}s", member.description)

            let subscriber = MemberSubscriptionService.Subscriber(roomExpress: roomExpress, queue: queue)
            let subscriptionService = MemberSubscriptionService(
                subscriber: subscriber,
                queue: queue,
                associatedMemberID: member.id
            )
            let subscriptionController = MemberSubscriptionController(
                member: member,
                service: subscriptionService,
                streamTokens: [
                    .audio(room.configuration.audioStreamToken),
                    .video(room.configuration.videoStreamToken)
                ],
                queue: queue
            )

            let memberController = MemberController(
                subscriptionController: subscriptionController,
                member: member,
                queue: queue
            )
            memberController.memberDelegate = self
            memberController.mediaDelegate = mediaDelegate

            // Save the new member controller
            memberControllers[member.id] = memberController

            memberController.start()
        }

        private func unsubscribeFromMemberStream(_ member: Member) {
            guard let memberController = memberControllers.removeValue(forKey: member.id) else {
                return
            }

            os_log(.debug, log: Self.logger, "Remove MemberController for: %{public}s", member.description)

            memberController.dispose()
        }

        /// Retrieve a member from the member list by its ID.
        /// - Parameter id: Member id.
        /// - Returns: Member instance, if it exists.
        private func member(withID id: Member.ID) -> Member? {
            members.first { $0.id == id }
        }

        /// Retrieve a member controller from the list by its member ID.
        /// - Parameter id: Member ID.
        /// - Returns: MemberController instance, if it exists.
        private func memberController(withID id: Member.ID) -> MemberController? {
            memberControllers[id]
        }

        private func process(removed member: Member) {
            os_log(
                .debug,
                log: Self.logger,
                "%{private}s, Process removed member: %{private}s",
                description,
                member.description
            )

            unsubscribeFromMemberStream(member)
        }

        private func process(added member: Member) {
            os_log(
                .debug,
                log: Self.logger,
                "%{private}s, Process added member: %{private}s",
                description,
                member.description
            )

            if room?.configuration.joinsSilently == false || allowsMemberSubscription {
                subscribeToMemberStream(member)
            }
        }
    }
}

extension PhenixCore.RoomController: CustomStringConvertible {
    var description: String {
        room?.alias ?? "n/a"
    }
}

// MARK: - Disposable
extension PhenixCore.RoomController: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "Dispose")

        roomMemberCancellable = nil
        roomMemberCountCancellable = nil

        memberControllers.values.forEach { $0.dispose() }
        memberControllers.removeAll()

        room?.dispose()
        room = nil
    }

    func resetRoomController() {
        os_log(.debug, log: Self.logger, "Reset")

        roomMemberCancellable = nil
        roomMemberCountCancellable = nil

        allowsMemberSubscription = false

        memberControllers.values.forEach { $0.dispose() }
        memberControllers.removeAll()

        room?.dispose()
        room = nil

        isLeaving = false

        membersSubject.send([])
        memberCountSubject.send(0)
    }
}

// MARK: - MemberSubscriptionInformationProvider
extension PhenixCore.RoomController: MemberSubscriptionInformationProvider {
    /// Checks if the limit of the member subscription with video is reached.
    /// - Returns: Bool, `true` - can subscribe with video, `false` - limit is reached, should not subscribe with video
    func canMemberSubscribeForVideo() -> Bool {
        let maxVideoSubscriptions = room?.configuration.maxVideoSubscriptions ?? 0

        // Calculate, how many of members have video subscription currently.
        let videoSubscriptions = memberControllers.values.reduce(into: 0) { result, memberController in
            result += memberController.memberSubscribesVideoStream ? 1 : 0
        }

        os_log(
            .debug,
            log: Self.logger,
            "Currently active video subscriptions: %{private}s/%{private}s",
            videoSubscriptions.description,
            maxVideoSubscriptions.description
        )

        return videoSubscriptions < maxVideoSubscriptions
    }
}

// MARK: MemberUpdater
extension PhenixCore.RoomController: MemberUpdater {
    func memberDidUpdate() {
        membersSubject.send(membersSubject.value)
    }
}
