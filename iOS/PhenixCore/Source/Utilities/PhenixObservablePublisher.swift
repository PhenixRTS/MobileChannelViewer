//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

struct PhenixObservablePublisher<Output, Observer>: Publisher {
    typealias Failure = Never

    fileprivate var observer: Observer

    init(observer: Observer) {
        self.observer = observer
    }

    // Combine will call this method on our publisher whenever
    // a new object started observing it. Within this method,
    // we'll need to create a subscription instance and
    // attach it to the new subscriber:
    func receive<S: Subscriber>(subscriber: S) where S.Input == Output, S.Failure == Failure {
        // Creating our custom subscription instance:
        let subscription = EventSubscription<S, Output>()
        subscription.target = subscriber

        // Attaching our subscription to the subscriber:
        subscriber.receive(subscription: subscription)

        guard let observer = observer as? PhenixObservable<AnyObject> else {
            return
        }

        subscription.disposable = observer.subscribe { changes in
            guard let streams = changes?.value as? Output else {
                return
            }

            subscription.trigger(streams)
        }
    }
}

fileprivate class EventSubscription<Target: Subscriber, Input>: Subscription where Target.Input == Input {
    var target: Target?
    var disposable: PhenixDisposable?

    func request(_ demand: Subscribers.Demand) {}

    func cancel() {
        target = nil
        disposable = nil
    }

    func trigger(_ item: Target.Input) {
        let _ = target?.receive(item)
    }
}
