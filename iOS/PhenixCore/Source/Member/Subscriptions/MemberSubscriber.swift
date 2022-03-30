//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import PhenixSdk

extension MemberSubscriptionService {
    class Subscriber: Identifiable {
        private let queue: DispatchQueue
        private let roomExpress: PhenixRoomExpress
        private var subscriptions: Set<Subscription> = []

        init(roomExpress: PhenixRoomExpress, queue: DispatchQueue) {
            self.queue = queue
            self.roomExpress = roomExpress
        }

        func subscribe(
            stream: PhenixStream,
            streamToken: String,
            completion: @escaping (Result<Subscription, SubscriptionError>) -> Void
        ) {
            queue.async { [weak self] in
                guard let self = self else {
                    completion(.failure(.subscriptionFailed(nil)))
                    return
                }

                let subscription = Subscription(queue: self.queue)
                self.subscriptions.insert(subscription)

                let options = StreamOptionsFactory
                    .makeSubscribeToMemberStreamOptions(streamToken: streamToken) { [weak subscription] reason in
                        subscription?.streamDidEnd(with: reason)
                    }

                self.roomExpress.subscribe(toMemberStream: stream, options) { [weak self, weak subscription] status, subscriber, _ in
                    self?.queue.async {
                        guard let self = self else {
                            completion(.failure(.subscriptionFailed(nil)))
                            return
                        }

                        guard let subscription = subscription else {
                            completion(.failure(.subscriptionFailed(nil)))
                            return
                        }

                        self.subscriptions.remove(subscription)

                        switch status {
                        case .ok:
                            guard let subscriber = subscriber else {
                                completion(.failure(.subscriptionFailed(nil)))
                                return
                            }

                            let renderer: PhenixRenderer = subscriber.createRenderer()
                            subscription.set(subscriber: subscriber, renderer: renderer)
                            completion(.success(subscription))

                        default:
                            completion(.failure(.subscriptionFailed(status)))
                        }
                    }
                }
            }
        }

        func removeActiveSubscriptions() {
            queue.async { [weak self] in
                self?.subscriptions.removeAll()
            }
        }
    }
}

// MARK: - Error
extension MemberSubscriptionService.Subscriber {
    enum SubscriptionError: Swift.Error {
        case subscriptionFailed(PhenixRequestStatus?)
    }
}
