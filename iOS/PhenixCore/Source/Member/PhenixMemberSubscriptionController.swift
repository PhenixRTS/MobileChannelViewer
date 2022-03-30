//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import os.log
import PhenixSdk

extension PhenixCore {
    /// A member subscription controller is responsible for member stream subscriptions and also handling Zombie members.
    class MemberSubscriptionController {
        private static let logger = OSLog(identifier: "MemberSubscriptionController")

        private let connectionStateWatchdogTimeInterval: TimeInterval = 10
        private let queue: DispatchQueue
        private let member: Member
        private let streamTokens: [StreamToken]
        private let subscriptionService: MemberSubscriptionService

        private var connectionStateWatchdog: Watchdog?

        private var subscribedStream: PhenixStream?
        private var subscriptions: Set<MemberSubscriptionService.Subscription> = []

        private var subscriptionCancellables: Set<AnyCancellable> = []
        private var memberStreamListCancellable: AnyCancellable?
        private var memberConnectionStateCancellable: AnyCancellable?

        private(set) var subscribesVideoStream = false

        weak var delegate: MemberSubscriptionControllerDelegate?

        init(
            member: Member,
            service: MemberSubscriptionService,
            streamTokens: [StreamToken],
            queue: DispatchQueue
        ) {
            self.queue = queue
            self.member = member
            self.streamTokens = streamTokens
            self.subscriptionService = service
            self.subscriptionService.delegate = self
        }

        func start() {
            os_log(.debug, log: Self.logger, "%{public}s, Start", member.id)

            memberConnectionStateCancellable = member.connectionStatePublisherInternal
                .dropFirst() // After initial observation, we do not need to process values, so we can drop it.
                .removeDuplicates()
                .sink { [weak self] state in
                    guard let self = self else { return }

                    switch state {
                    case .active:
                        self.stopConnectionStateWatchdog()

                    case .away:
                        self.startConnectionStateWatchdog()

                    case .pending:
                        self.member.secondaryPreviewLayer.flushAndRemoveImage()
                        self.stopConnectionStateWatchdog()
                        // Start stream subscription once again.
                        self.subscriptionService.subscribeStreams(self.member.streams)

                    case .removed:
                        self.stopConnectionStateWatchdog()
                    }
                }

            memberStreamListCancellable = member.streamsPublisher
                .sink { [weak self] streams in
                    self?.subscriptionService.subscribeStreams(streams)
                }
        }

        private func setMemberConnectionState(_ state: Member.ConnectionState) {
            os_log(
                .debug,
                log: Self.logger,
                "%{public}s, Set member connection state: %{public}s",
                member.id,
                state.description
            )

            member.setConnectionState(state)
        }

        // MARK: - Completion handlers

        private func onDataQualityChange(
            _ status: PhenixDataQualityStatus?,
            subscription: MemberSubscriptionService.Subscription
        ) {
            os_log(
                .debug,
                log: Self.logger,
                "%{public}s, Data quality changed: %{public}s",
                member.id,
                status?.description ?? "nil"
            )

            guard status == .noData else {
                setMemberConnectionState(.active)
                return
            }

            /*
             If a member backgrounds the app, member's video(!) data stream
             will stop publishing and therefore all other members will receive
             "noData"-quality callback even if the member still produces audio data.

             Unfortunately, we cannot trust the audio data quality, because
             the SDK generates silence audio, when the member does not
             generate any, therefore it always shows that the quality is fine.

             A workaround is to check if the appropriate stream media
             state is ON and only then move the member to the "away" state,
             if necessary.
             */

            guard (subscription.isAudioSubscription && member.isAudioEnabled)
                    || (subscription.isVideoSubscription && member.isVideoEnabled) else {
                return
            }

            setMemberConnectionState(.away)
        }

        private func onStreamEnded(reason: PhenixStreamEndedReason) {
            os_log(.debug, log: Self.logger, "%{public}s, Stream ended: %{public}s", member.id, reason.description)

            subscribesVideoStream = false
            disposeAllSubscriptions()
            setMemberConnectionState(.pending)
        }

        // MARK: - Watchdog

        private func startConnectionStateWatchdog() {
            guard connectionStateWatchdog == nil else {
                return
            }

            connectionStateWatchdog = Watchdog(
                timeInterval: connectionStateWatchdogTimeInterval,
                queue: queue
            ) { [weak self] in
                guard let self = self else { return }
                os_log(.debug, log: Self.logger, "%{private}s, Watchdog executed", self.member.id)
                // Member has been inactive for a while, move it to a `pending` state.
                self.setMemberConnectionState(.pending)
            }
            connectionStateWatchdog?.start()
        }

        private func stopConnectionStateWatchdog() {
            os_log(.debug, log: Self.logger, "%{private}s, Dispose watchdog", member.id)

            connectionStateWatchdog?.cancel()
            connectionStateWatchdog = nil
        }

        // MARK: - Disposable methods

        private func disposeAllSubscriptions() {
            os_log(.debug, log: Self.logger, "%{public}s, Dispose all subscriptions", member.id)

            subscribedStream = nil
            subscriptions.forEach { $0.dispose() }
            subscriptions.removeAll()
            subscriptionCancellables.removeAll()
        }
    }
}

// MARK: - SubscriptionServiceDelegate
extension PhenixCore.MemberSubscriptionController: MemberSubscriptionServiceDelegate {
    func subscriptionService(
        _ service: MemberSubscriptionService,
        shouldSubscribeTo stream: PhenixStream
    ) -> MemberSubscriptionService.SubscriptionAction {
        if member.isSelf {
            delegate?.subscriptionController(self, didReceiveDataFrom: stream)
            setMemberConnectionState(.active)
            return .stop
        } else if stream.getUri() == subscribedStream?.getUri() {
            /*
             Stream, to which we are currently trying to subscribe,
             already contains an active subscription. We can
             skip this stream, no need to subscribe to it once more.
             */
            return .skip
        } else {
            return .subscribe
        }
    }

    func subscriptionService(
        _ service: MemberSubscriptionService,
        didSubscribeTo stream: PhenixStream,
        with subscription: MemberSubscriptionService.Subscription
    ) {
        subscription.renderer.start(member.previewLayer)
    }

    func subscriptionService(_ service: MemberSubscriptionService, didFailToSubscribeTo stream: PhenixStream) {
        subscribesVideoStream = false
    }

    func subscriptionService(
        _ service: MemberSubscriptionService,
        didReceiveDataFrom stream: PhenixStream,
        with subscriptions: Set<MemberSubscriptionService.Subscription>
    ) {
        /*
         At this point, all the streams are receiving valid data and we know for sure that
         the stream is alive. So we can remove all previous subscriptions and save the new
         ones. And then subscribe for the data quality and stream-end events.
         */
        disposeAllSubscriptions()
        self.subscribedStream = stream
        self.subscriptions = subscriptions

        delegate?.subscriptionController(self, didReceiveDataFrom: stream, with: subscriptions)

        for subscription in subscriptions {
            subscription.dataQualityPublisher
                .receive(on: queue)
                .sink { [weak self] status in
                    self?.onDataQualityChange(status, subscription: subscription)
                }
                .store(in: &subscriptionCancellables)

            subscription.statePublisher
                .receive(on: queue)
                .sink { [weak self] state in
                    if case let MemberSubscriptionService.Subscription.StreamState.ended(reason) = state {
                        self?.onStreamEnded(reason: reason)
                    }
                }
                .store(in: &subscriptionCancellables)
        }
    }

    func subscriptionService(
        _ service: MemberSubscriptionService,
        tokensForSubscribing stream: PhenixStream
    ) -> [PhenixCore.StreamToken] {
        let shouldSubscribeVideoStream = delegate?.shouldSubscriptionControllerSubscribeVideoStream(self) ?? false
        /*
         Filter out all of the tokens, which are not needed.
         For example, if there are enough video streams already,
         then filter them out.
         */
        let tokens = streamTokens.filter { token in
            switch token {
            case .video:
                return shouldSubscribeVideoStream
            default:
                return true
            }
        }

        subscribesVideoStream = tokens.contains { token in
            switch token {
            case .video, .universal:
                return true
            default:
                return false
            }
        }

        assert(tokens.isEmpty == false, "At least one stream token should be provided for the stream subscription.")

        return tokens
    }
}

// MARK: - SubscriptionServiceDelegate
extension PhenixCore.MemberSubscriptionController: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{public}s, Dispose", member.id)

        subscriptionService.dispose()
        subscriptions.forEach { $0.dispose() }
    }
}
