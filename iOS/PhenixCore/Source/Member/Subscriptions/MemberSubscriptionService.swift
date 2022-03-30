//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

import UIKit

class MemberSubscriptionService {
    private static let logger = OSLog(identifier: "MemberSubscriptionService")

    private let associatedMemberID: String
    private let queue: DispatchQueue
    private let subscriber: Subscriber
    private let dataQualityTimeInterval: TimeInterval

    private var subscriptionWatchdog: Watchdog?
    private var streamCancellable: AnyCancellable?
    private var subscriptionDataQualityCancellables: Set<AnyCancellable> = []

    /// A Set with stream URIs to which the app failed to subscribe.
    ///
    /// If the app fails to subscribe to a stream, its URI gets saved into
    /// this failed stream list, so that in future the app would not retry to
    /// subscribe to it.
    private(set) var failureStreamURIs: Set<PhenixStream.URI> = []

    /// A list of available streams to try to subscribe.
    private(set) var candidateStreams: [PhenixStream] = []

    /// A stream, which is currently in process of subscription.
    private(set) var streamInProcess: PhenixStream?

    /// A counter of currently active subscribers, which are trying to subscribe to a provided stream.
    private(set) var subscriberCount = 0

    /// A Set of currently active subscriptions, which succeeded to subscribe to the stream.
    ///
    /// These subscriptions, when they will receive appropriate data quality callback will be moved to subscription controller and removed from here.
    private(set) var subscriptions: Set<Subscription> = []

    weak var delegate: MemberSubscriptionServiceDelegate?

    init(
        subscriber: Subscriber,
        queue: DispatchQueue,
        associatedMemberID: String,
        dataQualityTimeInterval: TimeInterval = 10
    ) {
        self.queue = queue
        self.subscriber = subscriber
        self.associatedMemberID = associatedMemberID
        self.dataQualityTimeInterval = dataQualityTimeInterval
    }

    func subscribeStreams(_ streams: [PhenixStream]) {
        os_log(
            .debug,
            log: Self.logger,
            "%{private}s, Subscribe streams: %{private}s",
            associatedMemberID,
            streams.description
        )

        streamCancellable = Just(streams)
            .receive(on: queue)
            .handleEvents(receiveOutput: { [weak self] streams in
                guard let self = self else { return }
                /*
                 Clear the failure stream list out of absent streams,
                 which does not show up in the [PhenixStream] list anymore.
                 */
                self.failureStreamURIs = Self.keep(streams, in: self.failureStreamURIs)
            })
            .compactMap { [weak self] streams -> [PhenixStream]? in
                guard let self = self else { return nil }
                /*
                 Clear out the streams list from the streams,
                 which previously were marked as failure streams.
                 */
                let output = Self.remove(self.failureStreamURIs, from: streams)

                if output.isEmpty == false {
                    return output
                } else {
                    return nil
                }
            }
            .handleEvents(receiveOutput: { [weak self] streams in
                // Clear all of the previous meta information.
                self?.resetServiceState()
            })
            .sink { [weak self] streams in
                self?.candidateStreams = streams
                self?.subscribeNextCandidateStream()
            }
    }

    /// A method which marks the provided stream as a failure stream and removes it from the candidate stream list.
    /// - Parameter stream: A stream, which can not be subscribed successfully.
    private func markStreamAsFailure(_ stream: PhenixStream) {
        os_log(
            .debug,
            log: Self.logger,
            "%{private}s, Mark as failure stream: %{private}s",
            associatedMemberID,
            stream.description
        )
        // Mark current stream as a failure stream.
        failureStreamURIs.insert(stream.getUri())
        removeCandidateStream(stream)
    }

    private func removeCandidateStream(_ stream: PhenixStream) {
        guard let index = candidateStreams.firstIndex(where: { $0.getUri() == stream.getUri() }) else {
            return
        }

        candidateStreams.remove(at: index)
    }

    // MARK: - Subscription methods

    private func subscribeNextCandidateStream() {
        os_log(.debug, log: Self.logger, "%{private}s, Subscribe next candidate stream", associatedMemberID)
        let provider = MemberNextStreamProvider(streams: candidateStreams, previouslyProvidedStream: streamInProcess)

        if let stream = try? provider.nextStream() {
            do {
                try subscribeToStreamIfNeeded(stream)
            } catch {
                subscribeNextCandidateStream()
            }
        }
    }

    private func subscribeToStreamIfNeeded(_ stream: PhenixStream) throws {
        streamInProcess = stream

        os_log(
            .debug,
            log: Self.logger,
            "%{private}s, Try to subscribe stream: %{private}s",
            associatedMemberID,
            stream.description
        )

        let action = delegate?.subscriptionService(self, shouldSubscribeTo: stream) ?? .stop

        switch action {
        case .stop:
            os_log(.debug, log: Self.logger, "%{private}s, Stop stream subscription", associatedMemberID)
            candidateStreams = []
            streamInProcess = nil

        case .skip:
            os_log(.debug, log: Self.logger, "%{private}s, Skip stream subscription", associatedMemberID)
            subscribeNextCandidateStream()

        case .subscribe:
            os_log(.debug, log: Self.logger, "%{private}s, Can subscribe stream", associatedMemberID)
            try subscribeToStream(stream)
        }
    }

    private func subscribeToStream(_ stream: PhenixStream) throws {
        os_log(
            .debug,
            log: Self.logger,
            "%{private}s, Subscribe stream: %{private}s",
            associatedMemberID,
            stream.description
        )

        let tokens = delegate?.subscriptionService(self, tokensForSubscribing: stream) ?? []

        guard tokens.isEmpty == false else {
            os_log(
                .debug,
                log: Self.logger,
                "%{private}s, No stream tokens provided, skip stream",
                associatedMemberID
            )

            delegate?.subscriptionService(self, didFailToSubscribeTo: stream)
            throw SubscriptionError.noStreamTokens
        }

        subscriberCount = tokens.count

        for streamToken in tokens {
            subscriber.subscribe(stream: stream, streamToken: streamToken.value) { [weak self] result in
                switch result {
                case .success(let subscription):
                    self?.onSucceedToSubscribeStream(stream, subscription: subscription)
                case .failure:
                    self?.onFailedToSubscribeStream(stream)
                }
            }
        }
    }

    // MARK: - Completion handlers

    private func onFailedToSubscribeStream(_ stream: PhenixStream) {
        os_log(
            .debug,
            log: Self.logger,
            "%{private}s, Stream subscribtion failed: %{private}s",
            associatedMemberID,
            stream.description
        )

        /*
         Remove all subscribers for this stream, because it can be
         considered as a dead-stream, and there is no point of
         trying to subscribe to it anymore.
         */
        disposeSubscriptionWatchdog()
        disposeAllSubscribers()
        disposeAllSubscriptions()
        disposeAllStreamDataQualitySubscriptions()

        markStreamAsFailure(stream)

        delegate?.subscriptionService(self, didFailToSubscribeTo: stream)

        subscribeNextCandidateStream()
    }

    private func onSucceedToSubscribeStream(_ stream: PhenixStream, subscription: Subscription) {
        os_log(
            .debug,
            log: Self.logger,
            "%{private}s, Stream subscribtion succeeded: %{private}s",
            associatedMemberID,
            stream.description
        )

        subscriberCount -= 1

        os_log(
            .debug,
            log: Self.logger,
            "%{private}s, Remaing %{private}i pedning subscriptions",
            associatedMemberID,
            subscriberCount
        )

        subscriptions.insert(subscription)

        delegate?.subscriptionService(self, didSubscribeTo: stream, with: subscription)
        startSubscriptionWatchdogIfNeeded()

        subscription.dataQualityPublisher
            .receive(on: queue)
            .sink { [weak self] status in
                self?.onStreamDataQualityChange(stream, status: status)
            }
            .store(in: &self.subscriptionDataQualityCancellables)

        subscription.observeDataQuality()
    }

    private func onStreamDataQualityChange(_ stream: PhenixStream, status: PhenixDataQualityStatus?) {
        os_log(
            .debug,
            log: Self.logger,
            "%{public}s, Data quality changed: %{public}s",
            associatedMemberID,
            status?.description ?? "nil"
        )

        guard status == .all || status == .audioOnly else {
            return
        }

        /*
         Check if all subscribers have finished subscribing and
         all subscriptions have non-noData quality status.
         Otherwise keep waiting till all of the candidate stream
         subscriptions produce valid data back or when watchdog
         will interupt.
         */
        guard subscriberCount == 0 else {
            return
        }

        let candidateSubscriptionsAreValid = self.subscriptions.allSatisfy {
            $0.dataQuality == .all || $0.dataQuality == .audioOnly
        }

        guard candidateSubscriptionsAreValid else {
            return
        }

        /*
         All subscriptions produce valid data, stream is alive.
         Begin clean-up process and move subscriptions away from
         this service.
         */

        streamInProcess = nil
        disposeSubscriptionWatchdog()

        disposeAllSubscribers()
        disposeAllStreamDataQualitySubscriptions()

        let subscriptions = self.subscriptions

        /*
         Clear out the subscriptions list to be able to keep
         only the new subscriptions for the next stream
         subscription attempt.
         */
        self.subscriptions.removeAll()

        os_log(.debug, log: Self.logger, "%{private}s, Subscribed streams produce data", associatedMemberID)

        /*
         Pass the subscriptions to the delegate to take care of them.
         That is not the resposnibility of this service after a successful subscription
         */
        delegate?.subscriptionService(self, didReceiveDataFrom: stream, with: subscriptions)
    }

    // MARK: - Watchdog

    private func startSubscriptionWatchdogIfNeeded() {
        guard subscriptionWatchdog == nil else {
            return
        }

        subscriptionWatchdog = makeSubscriptionWatchdog()
        subscriptionWatchdog?.start()
    }

    private func makeSubscriptionWatchdog() -> Watchdog {
        Watchdog(timeInterval: dataQualityTimeInterval, queue: queue) { [weak self] in
            guard let self = self else {
                return
            }

            os_log(.debug, log: Self.logger, "%{private}s, Watchdog executed", self.associatedMemberID)

            self.disposeSubscriptionWatchdog()
            self.disposeAllSubscribers()
            self.disposeAllSubscriptions()
            self.disposeAllStreamDataQualitySubscriptions()

            self.subscribeNextCandidateStream()
        }
    }

    private func disposeSubscriptionWatchdog() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose watchdog", associatedMemberID)
        subscriptionWatchdog?.cancel()
        subscriptionWatchdog = nil
    }

    // MARK: - Disposable methods

    private func resetServiceState() {
        os_log(.debug, log: Self.logger, "%{private}s, Reset", associatedMemberID)
        streamInProcess = nil
        candidateStreams.removeAll()

        disposeSubscriptionWatchdog()
        disposeAllSubscribers()
        disposeAllSubscriptions()
        disposeAllStreamDataQualitySubscriptions()
    }

    private func disposeAllSubscribers() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose all subscribers", associatedMemberID)
        subscriber.removeActiveSubscriptions()
        subscriberCount = 0
    }

    private func disposeAllSubscriptions() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose all subscriptions", associatedMemberID)
        subscriptions.forEach { $0.dispose() }
        subscriptions.removeAll()
    }

    private func disposeAllStreamDataQualitySubscriptions() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose all data quality subscriptions", associatedMemberID)
        subscriptionDataQualityCancellables.removeAll()
    }

    // MARK: - Other methods

    private static func remove(_ streamURIs: Set<PhenixStream.URI>, from streams: [PhenixStream]) -> [PhenixStream] {
        var streamsCopy = streams
        streamsCopy.removeAll { streamURIs.contains($0.getUri()) }
        return streamsCopy
    }

    private static func keep(_ streams: [PhenixStream], in streamURIs: Set<PhenixStream.URI>) -> Set<PhenixStream.URI> {
        let arrayOfStreamURIs = streams.compactMap { $0.getUri() }
        let setOfStreamURIs = Set(arrayOfStreamURIs)

        let result = streamURIs.filter { uri in
            setOfStreamURIs.contains(uri)
        }

        return result
    }
}

// MARK: - Disposable
extension MemberSubscriptionService: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "Dispose")

        streamCancellable = nil

        disposeSubscriptionWatchdog()
        disposeAllSubscribers()
        disposeAllSubscriptions()
        disposeAllStreamDataQualitySubscriptions()
    }
}

// MARK: - Extension

extension MemberSubscriptionService {
    enum SubscriptionError: Error {
        case noStream
        case noStreamTokens
    }
}

extension MemberSubscriptionService {
    /// A type which informs about what kind of action needs to be taken with the stream subscription.
    enum SubscriptionAction {
        /// Subscribes to the provided stream.
        case subscribe

        /// Do not subscribe to provided stream and switch to the next one.
        ///
        /// - Warning: Be careful, if the list of streams will contain multiple entries,
        /// the script can get stuck in a endless loop if you skip each of the stream subscription.
        case skip

        /// Do not subscribe to provided stream and stop the subscription process at all.
        ///
        /// If this action will be executed, then only posibility to start subscription
        /// will be only if the Member's stream list will reload.
        case stop
    }
}
