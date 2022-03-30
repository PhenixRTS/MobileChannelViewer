//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

public extension PhenixCore {
    final class Room: Identifiable {
        private static let logger = OSLog(identifier: "Room")

        private let queue: DispatchQueue
        private let selfMember: Member
        private let publisherQueue: DispatchQueue

        private let joinRoomService: PhenixRoomService
        private var publishRoomService: PhenixRoomService?

        private var sizeSubject = CurrentValueSubject<Int, Never>(0)
        private var membersSubject = CurrentValueSubject<[Member], Never>([])

        private var mediaPublisher: PhenixExpressPublisher?

        private var roomDisposable: PhenixDisposable?
        private var roomMemberListDisposable: PhenixDisposable?
        private var roomMemberCountDisposable: PhenixDisposable?

        var configuration: Configuration
        var roomService: PhenixRoomService { joinRoomService }

        var isPublishing: Bool { mediaPublisher != nil }

        lazy var sizePublisher: AnyPublisher<Int, Never> = sizeSubject.eraseToAnyPublisher()
        lazy var membersPublisher: AnyPublisher<[Member], Never> = membersSubject.eraseToAnyPublisher()

        public var alias: String { configuration.alias }
        public var id: String { roomService.getObservableActiveRoom().getValue().getId() }

        init(
            roomService: PhenixRoomService,
            configuration: Configuration,
            queue: DispatchQueue = .main,
            publisherQueue: DispatchQueue = .main
        ) {
            self.queue = queue
            self.joinRoomService = roomService
            self.configuration = configuration
            self.publisherQueue = publisherQueue

            // Create the self-member
            let transformer = MemberTransformer(roomService: roomService, publisherQueue: publisherQueue)
            self.selfMember = transformer.transform(roomService.getSelf())
        }

        func start() {
            /*
             Normally, while the self-member isn't joined the Room
             with the `participant` or the `moderator` role,
             it will not show up in the members list.
             So we are manually adding the self-member to the list
             when the room is created and configured to start
             producing data. This is important to provide for the
             app the possibility to take action on different
             use-cases for the member if necessary.
             For example, decide what kind of view to show for the
             user depending on its role.
             */
            membersSubject.send([selfMember])

            subscribeToRoomUpdates(joinRoomService)
        }

        /// Provides a media publisher to the Room instance, where it is kept alive.
        /// - Parameter publisher: Media publisher
        func setPublisher(_ publisher: PhenixExpressPublisher, publisherRoomService: PhenixRoomService) {
            mediaPublisher = publisher
            publishRoomService = publisherRoomService
        }

        func stopPublishing() {
            os_log(.debug, log: Self.logger, "%{private}s, Stop publishing", alias)

            mediaPublisher?.stop()
            mediaPublisher = nil
            publishRoomService = nil
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

        func leave(then handler: (() -> Void)? = nil) {
            os_log(.debug, log: Self.logger, "%{public}s, Leave room", alias)

            /*
             PhenixSdk 2022.0.1
             With this version, before leaving the room, we need explicitly "stop()"
             the publisher. Otherwise it will start to leak memory (every created
             instance of the ExpressPublisher will be kept alive in the PhenixSDK).
             */
            stopPublishing()
            joinRoomService.leaveRoom { _, _ in handler?() }
        }

        // MARK: - Private methods

        private func transform(_ members: [PhenixMember], roomService: PhenixRoomService) -> [Member] {
            let transformer = MemberTransformer(roomService: roomService, publisherQueue: publisherQueue)
            return transformer.transform(members)
        }

        private func subscribeToRoomUpdates(_ roomService: PhenixRoomService) {
            os_log(.debug, log: Self.logger, "%{private}s, Subscribe to room updates", alias)

            roomDisposable = roomService
                .getObservableActiveRoom()
                .subscribe { [weak self] changes in
                    self?.roomDidChange(changes)
                }
        }

        private func subscribeToEstimatedMemberCountUpdates(_ room: PhenixRoom) {
            os_log(.debug, log: Self.logger, "%{private}s, Subscribe to estimated member count updates", alias)

            roomMemberCountDisposable = room
                .getObservableEstimatedSize()
                .subscribe { [weak self] changes in
                    self?.estimatedRoomSizeDidChange(changes)
                }
        }

        private func subscribeToMemberListUpdates(_ room: PhenixRoom) {
            os_log(.debug, log: Self.logger, "%{private}s, Subscribe to member list updates", alias)

            roomMemberListDisposable = room
                .getObservableMembers()
                .subscribe { [weak self] changes in
                    self?.memberListDidChange(changes)
                }
        }

        // MARK: - Observable callback methods

        private func roomDidChange(_ changes: PhenixObservableChange<PhenixRoom>?) {
            queue.async { [weak self] in
                guard let self = self else {
                    return
                }

                guard let room = changes?.value as? PhenixRoom else {
                    return
                }

                os_log(.debug, log: Self.logger, "%{private}s, Room changed")

                // We need to refresh the whole members.
                self.membersSubject.send([self.selfMember])

                self.subscribeToEstimatedMemberCountUpdates(room)
                self.subscribeToMemberListUpdates(room)
            }
        }

        private func estimatedRoomSizeDidChange(_ changes: PhenixObservableChange<NSNumber>?) {
            queue.async { [weak self] in
                guard let count = changes?.value as? Int else {
                    return
                }

                self?.sizeSubject.send(count)
            }
        }

        private func memberListDidChange(_ changes: PhenixObservableChange<NSArray>?) {
            /*
             Member state change does not trigger the room member list update.
             Only member role change triggers room member list to update and execute callback.
             */

            queue.async { [weak self] in
                guard let self = self else {
                    return
                }

                guard let rawMembers = changes?.value as? [PhenixMember] else {
                    return
                }

                os_log(.debug, log: Self.logger, "%{private}s, Member list changed", self.alias)

                var newMembers = self.transform(rawMembers, roomService: self.joinRoomService)
                newMembers = self.appendSelfMember(newMembers)

                var currentMembers = self.membersSubject.value
                let changes = newMembers.difference(from: currentMembers).inferringMoves()

                for change in changes {
                    switch change {
                    case .insert(_, let member, let association):
                        /*
                         If the associated index isn't nil, that means
                         that the insert is associated with a removal,
                         and we can treat it as a move.
                         */
                        if association == nil {
                            currentMembers.append(member)
                        }
                    case .remove(let index, _, let association):
                        /*
                         We'll only process removals if the associated
                         index is nil, since otherwise we will already
                         have handled that operation as a move above.
                         */
                        if association == nil {
                            currentMembers.remove(at: index)
                        }
                    }
                }

                /*
                 Each time the member's list is updated, all of those members are created from scratch,
                 that means that also all subscriptions are recreated. We do not want to do that for the
                 already existing members, we need to do that only for the new members!
                 Therefore we are removing the old members and adding new members manually from the current
                 member list (re-using the previous members).
                 */

                self.membersSubject.send(currentMembers)
            }
        }

        private func appendSelfMember(_ members: [Member]) -> [Member] {
            var newMembers: [Member] = [selfMember]

            /*
             In cases, when the self-member is publishing, then it will already be in the new member list.
             We need to replace it with our existing instance of the self-member.
             */
            for member in members where member.id != selfMember.id {
                newMembers.append(member)
            }

            return newMembers
        }
    }
}

// MARK: - CustomStringConvertible
extension PhenixCore.Room: CustomStringConvertible {
    public var description: String {
        "Room(alias: \(alias))"
    }
}

// MARK: - Disposable
extension PhenixCore.Room: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose", alias)

        roomDisposable = nil
        roomMemberListDisposable = nil
        roomMemberCountDisposable = nil

        stopPublishing()
        membersSubject.value.forEach { $0.dispose() }
        membersSubject.send([])
    }
}

// MARK: - Equatable
extension PhenixCore.Room: Equatable {
    public static func == (lhs: PhenixCore.Room, rhs: PhenixCore.Room) -> Bool {
        lhs.alias == rhs.alias
    }
}

// MARK: - Hashable
extension PhenixCore.Room: Hashable {
    public func hash(into hasher: inout Hasher) {
        hasher.combine(alias)
    }
}
