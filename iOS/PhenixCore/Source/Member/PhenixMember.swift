//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

public extension PhenixCore {
    final class Member: Identifiable {
        private static let logger = OSLog(identifier: "Member")

        private let phenixMember: PhenixMember
        private let propertyPublisherQueue: DispatchQueue

        private var volumeSubject = CurrentValueSubject<Volume, Never>(.volume0)
        private var isSelectedSubject = CurrentValueSubject<Bool, Never>(false)
        private var isAudioEnabledSubject = CurrentValueSubject<Bool, Never>(false)
        private var isVideoEnabledSubject = CurrentValueSubject<Bool, Never>(false)
        private var connectionStateSubject = CurrentValueSubject<ConnectionState, Never>(.pending)

        let previewLayer: VideoLayer
        let secondaryPreviewLayer: VideoLayer

        var streams: [PhenixStream] {
            phenixMember.getObservableStreams().getValueOrDefault() as? [PhenixStream] ?? []
        }

        var streamsPublisher: AnyPublisher<[PhenixStream], Never> { phenixMember.streamsPublisher }

        public var volume: Volume { volumeSubject.value }
        public var isSelected: Bool { isSelectedSubject.value }
        public var isAudioEnabled: Bool { isAudioEnabledSubject.value }
        public var isVideoEnabled: Bool { isVideoEnabledSubject.value }
        public var connectionState: ConnectionState { connectionStateSubject.value }

        public private(set) var isSelf: Bool

        public lazy var volumePublisher: AnyPublisher<Volume, Never> = volumeSubject
            .removeDuplicates()
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var isSelectedPublisher: AnyPublisher<Bool, Never> = isSelectedSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var isAudioEnabledPublisher: AnyPublisher<Bool, Never> = isAudioEnabledSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var isVideoEnabledPublisher: AnyPublisher<Bool, Never> = isVideoEnabledSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        public lazy var connectionStatePublisher: AnyPublisher<ConnectionState, Never> = connectionStateSubject
            .receive(on: propertyPublisherQueue)
            .eraseToAnyPublisher()

        /// A member's connection state publisher, which does not publish changes on the provided publisher's queue (mainly it is the main queue).
        ///
        /// Use this publisher inside the PhenixCore, to be able to control the flow in a thread safe manner,
        /// rather than the public ``connectionStatePublisher``.
        lazy var connectionStatePublisherInternal = connectionStateSubject.eraseToAnyPublisher()

        public var id: String { phenixMember.getSessionId() }
        public var name: String { phenixMember.getObservableScreenName().getValueOrDefault() as? String ?? "" }

        public var role: Role {
            if let role = PhenixMemberRole(rawValue: phenixMember.getObservableRole().getValueOrDefault().intValue) {
                return Member.Role(phenixMemberRole: role) ?? .audience
            } else {
                return .audience
            }
        }

        public var state: State {
            if let state = PhenixMemberState(rawValue: phenixMember.getObservableState().getValueOrDefault().intValue) {
                return Member.State(phenixMemberState: state)
            } else {
                return .active
            }
        }

        public var lastUpdate: Date {
            phenixMember.getObservableLastUpdate().getValueOrDefault() as? Date ?? .distantPast
        }

        init(
            phenixMember: PhenixMember,
            isSelf: Bool,
            previewLayer: VideoLayer = .init(),
            secondaryPreviewLayer: VideoLayer = .init(),
            publisherQueue: DispatchQueue = .main
        ) {
            self.phenixMember = phenixMember
            self.isSelf = isSelf
            self.previewLayer = previewLayer
            self.propertyPublisherQueue = publisherQueue
            self.secondaryPreviewLayer = secondaryPreviewLayer
            secondaryPreviewLayer.videoGravity = .resizeAspectFill
        }

        func update(role: Role? = nil, state: State? = nil, name: String? = nil) {
            var commitChanges = false

            /*
             SDK 2021.0.11
             SDK has an issue, when the user role is already
             set to `audience` and the code tries to set it
             once again to the `audience` after short period
             of time, then `observableMembers` parameter
             will include members with role `audience` in the
             list, which should not happen.
             */

            if let role = role {
                os_log(.debug, log: Self.logger, "%{private}s, Member update, role: %{private}s", id, role.description)
                phenixMember.getObservableRole().setValue(role.phenixMemberRole.rawValue as NSNumber)
                commitChanges = true
            }

            if let state = state {
                os_log(.debug, log: Self.logger, "%{private}s, Member update, state: %{private}s", id, state.description)
                phenixMember.getObservableState().setValue(state.phenixMemberState.rawValue as NSNumber)
                commitChanges = true
            }

            if let name = name {
                os_log(.debug, log: Self.logger, "%{private}s, Member update, name: %{private}s", id, name.description)
                phenixMember.getObservableScreenName().setValue(name as NSString)
                commitChanges = true
            }

            guard commitChanges else {
                return
            }

            phenixMember.commitChanges { [weak self] status, string in
                guard let self = self else {
                    return
                }

                os_log(
                    .debug,
                    log: Self.logger,
                    "%{private}s, Member update finished: %{private}s %{private}s",
                    self.id,
                    status.description,
                    string ?? ""
                )

                if status != .ok {
                    self.phenixMember.reload()
                }
            }
        }

        func setAudio(enabled: Bool) {
            os_log(.debug, log: Self.logger, "%{private}s, Set audio: %{private}s", id, enabled.description)
            isAudioEnabledSubject.send(enabled)
        }

        func setVideo(enabled: Bool) {
            os_log(.debug, log: Self.logger, "%{private}s, Set video: %{private}s", id, enabled.description)
            isVideoEnabledSubject.send(enabled)
        }

        func setConnectionState(_ state: ConnectionState) {
            os_log(.debug, log: Self.logger, "%{private}s, Set connection state: %{private}s", id, state.description)
            connectionStateSubject.send(state)
        }

        func setSelection(enabled: Bool) {
            os_log(.debug, log: Self.logger, "%{private}s, Set selection: %{private}s", id, enabled.description)
            isSelectedSubject.send(enabled)
        }

        func setVolume(_ volume: Volume) {
            volumeSubject.send(volume)
        }

        func setSecondaryVideoFrame(_ sampleBuffer: CMSampleBuffer) {
            if secondaryPreviewLayer.isReadyForMoreMediaData {
                secondaryPreviewLayer.enqueue(sampleBuffer)
            }
        }

        func dropLastSecondaryVideoFrame() {
            secondaryPreviewLayer.flushAndRemoveImage()
        }

        func renderVideo(layer: CALayer?) {
            os_log(.debug, log: Self.logger, "%{private}s, Render on image: %{private}s", id, String(describing: layer))

            guard isSelf == false else {
                os_log(.debug, log: Self.logger, "%{private}s, Failed to render, this is self-member", id)
                return
            }

            guard layer != previewLayer.superlayer else {
                return
            }

            previewLayer.set(on: layer)
        }

        func renderThumbnailVideo(layer: CALayer?) {
            os_log(.debug, log: Self.logger, "%{private}s, Render on image: %{private}s", id, String(describing: layer))

            guard isSelf == false else {
                os_log(.debug, log: Self.logger, "%{private}s, Failed to render, this is self-member", id)
                return
            }

            guard layer != secondaryPreviewLayer.superlayer else {
                return
            }

            if layer == nil {
                /*
                 If the preview layer will be removed from the super layer,
                 then there is no need to continue frame observing.
                 */
                dropLastSecondaryVideoFrame()
            }

            secondaryPreviewLayer.set(on: layer)
        }
    }
}

// MARK: - CustomStringConvertible
extension PhenixCore.Member: CustomStringConvertible {
    public var description: String {
        """
        Member(\
        id: \(id), \
        name: \(name), \
        isSelf: \(isSelf), \
        role: \(role.description), \
        state: \(state.description), \
        connection: \(connectionState.description))
        """
    }
}

// MARK: - Disposable
extension PhenixCore.Member: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose", description)

        previewLayer.set(on: nil)
        secondaryPreviewLayer.set(on: nil)
    }
}

// MARK: - Hashable
extension PhenixCore.Member: Hashable {
    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

// MARK: - Equatable
extension PhenixCore.Member: Equatable {
    public static func == (lhs: PhenixCore.Member, rhs: PhenixCore.Member) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Comparable
extension PhenixCore.Member: Comparable {
    public static func < (lhs: PhenixCore.Member, rhs: PhenixCore.Member) -> Bool {
        return lhs.lastUpdate < rhs.lastUpdate
    }
}

// MARK: Collection extension for PhenixCore.Member
extension Collection where Element: PhenixCore.Member {
    public func selfMember() -> Self.Element? { first(where: \.isSelf) }
}
