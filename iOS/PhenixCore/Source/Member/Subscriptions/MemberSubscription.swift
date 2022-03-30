//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import PhenixSdk

extension MemberSubscriptionService {
    class Subscription {
        private let id: String
        private let queue: DispatchQueue

        private var stateSubject = CurrentValueSubject<StreamState, Never>(.active)
        private var dataQualitySubject = CurrentValueSubject<PhenixDataQualityStatus, Never>(.noData)

        lazy var statePublisher: AnyPublisher<StreamState, Never> = stateSubject
            .receive(on: queue)
            .eraseToAnyPublisher()

        lazy var dataQualityPublisher: AnyPublisher<PhenixDataQualityStatus, Never> = dataQualitySubject
            .receive(on: queue)
            .removeDuplicates()
            .eraseToAnyPublisher()

        private(set) var renderer: PhenixRenderer!
        private(set) var subscriber: PhenixExpressSubscriber!

        var dataQuality: PhenixDataQualityStatus? {
            dataQualitySubject.value
        }

        var audioTracks: [PhenixMediaStreamTrack] {
            subscriber.getAudioTracks()
        }

        var videoTracks: [PhenixMediaStreamTrack] {
            subscriber.getVideoTracks()
        }

        var isAudioSubscription: Bool {
            audioTracks.isEmpty == false
        }

        var isVideoSubscription: Bool {
            videoTracks.isEmpty == false
        }

        init(queue: DispatchQueue) {
            self.id = UUID().uuidString
            self.queue = queue
        }

        init(subscriber: PhenixExpressSubscriber, renderer: PhenixRenderer, queue: DispatchQueue) {
            self.id = UUID().uuidString
            self.queue = queue
            self.renderer = renderer
            self.subscriber = subscriber
        }

        /// Provide inner subscriber and renderer parameters, which could be initialized later, when the subscription instance is already created.
        /// - Parameters:
        ///   - subscriber: Inner PhenixSDK subscriber instance, which is returned by the SDK after a successful subscription.
        ///   - renderer: Inner PhenixSDK renderer instance.
        func set(subscriber: PhenixExpressSubscriber, renderer: PhenixRenderer) {
            guard self.subscriber == nil && self.renderer == nil else {
                assertionFailure("Subscriber and Renderer can be provided only once to a subscription object.")
                return
            }

            self.subscriber = subscriber
            self.renderer = renderer

            stateSubject.send(.active)
        }

        func observeDataQuality() {
            guard let renderer = renderer else {
                assertionFailure("Renderer should be provided, before starting to observe the data quality.")
                return
            }

            renderer.setDataQualityChangedCallback { [weak self] currentRenderer, status, reason in
                self?.dataQualitySubject.send(status)
            }
        }

        func streamDidEnd(with reason: PhenixStreamEndedReason) {
            stateSubject.send(.ended(reason))
        }
    }
}

extension MemberSubscriptionService.Subscription {
    enum StreamState {
        case active
        case ended(PhenixStreamEndedReason)
    }
}

// MARK: - CustomStringConvertible
extension MemberSubscriptionService.Subscription: CustomStringConvertible {
    var description: String {
        "Subscription(\(id))"
    }
}

// MARK: - Equatable
extension MemberSubscriptionService.Subscription: Equatable {
    static func == (lhs: MemberSubscriptionService.Subscription, rhs: MemberSubscriptionService.Subscription) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Hashable
extension MemberSubscriptionService.Subscription: Hashable {
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

// MARK: - Disposable
extension MemberSubscriptionService.Subscription {
    func dispose() {
        subscriber?.stop()
        renderer?.stop()
        renderer?.setDataQualityChangedCallback(nil)
    }
}
