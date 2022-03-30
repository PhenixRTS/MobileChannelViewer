//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

protocol MemberSubscriptionServiceDelegate: AnyObject {
    /// A delegate method, which executes before trying to subscribe to the stream and asks the delegate for approval.
    /// - Parameters:
    ///   - service: Subscription service.
    ///   - stream: A stream, to which the service is planning to subscribe.
    /// - Returns: If the return value is ``MemberSubscriptionService/SubscriptionAction/subscribe``
    /// then the service will continue with the subscription. If the return value is ``MemberSubscriptionService/SubscriptionAction/skip``
    /// then the service will skip current stream and move on to the next stream subscription if possible.
    /// If the return value is ``MemberSubscriptionService/SubscriptionAction/stop``
    /// then the service will abort the subscription process and won't do anything.
    func subscriptionService(
        _ service: MemberSubscriptionService,
        shouldSubscribeTo stream: PhenixStream
    ) -> MemberSubscriptionService.SubscriptionAction

    /// A delegate method, which is executed when a specific subscription is successfully subscribed.
    ///
    /// At this point the subscription's renderer should be started, so that it would be possible for the subscription
    /// service to wait for the quality data callback results.
    ///
    /// A successful subscription does not mean yet that the subscription's stream will produce valid data.
    /// There is still a chance that the stream is dead and will not produce any data.
    ///
    /// To be sure that the stream is valid and is producing data,
    /// wait for the ``subscriptionService(_:didReceiveDataFrom:)`` method execution.
    /// - Parameters:
    ///   - service: Subscription service.
    ///   - stream: A stream, to which the service just subscribed to.
    ///   - subscription: A subscription, which currently was successfully subscribed.
    func subscriptionService(
        _ service: MemberSubscriptionService,
        didSubscribeTo stream: PhenixStream,
        with subscription: MemberSubscriptionService.Subscription
    )

    func subscriptionService(_ service: MemberSubscriptionService, didFailToSubscribeTo stream: PhenixStream)

    /// A delegate method, which executes when all subscriptions from one stream starts to produce data.
    ///
    /// When the data is produced, it is safe to asume that this stream is valid and it needs to be kept.
    /// - Note: Keep the set of ``MemberStreamSubscriptionService/Subscription`` in memory, otherwise they will be unsubscribed.
    /// - Parameters:
    ///   - service: Subscription service.
    ///   - stream: A stream, to which the subscriptions are linked to.
    ///   - subscriptions: A set of subscriptions which produced a valid data.
    func subscriptionService(
        _ service: MemberSubscriptionService,
        didReceiveDataFrom stream: PhenixStream,
        with subscriptions: Set<MemberSubscriptionService.Subscription>
    )

    /// A delegate method which asks for the stream subscription tokens to be able to subscribe to the provided stream.
    /// - Parameters:
    ///   - service: Subscription service.
    ///   - stream: A stream, to which the service is about to subscribe.
    /// - Returns: List of stream tokens.
    func subscriptionService(
        _ service: MemberSubscriptionService,
        tokensForSubscribing stream: PhenixStream
    ) -> [PhenixCore.StreamToken]
}
