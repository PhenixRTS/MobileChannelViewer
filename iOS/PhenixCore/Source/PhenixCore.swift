//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import PhenixSdk
import UIKit

/// A main entry point for the PhenixCore framework.
///
/// A PhenixCore class knows how to interact with the dependency framework PhenixSdk.
/// It provides an easy-to-use public APIs and handles all of the business logic inside it.
///
/// To begin using it:
/// 1. Initialize a new instance of this class.
///     ```
///     let configuration = PhenixCore.Configuration(…)
///     let core = PhenixCore(configuration: configuration)
///     ```
///
/// 1. Subscribe to ``eventPublisher`` to receive latest events and be able to react to them.
///     ```
///     core.eventPublisher
///         .sink { completion in
///             print("Received completion:", completion)
///         } receiveValue: { event in
///             print("Received event:", event)
///         }
///         .store(in: &cancellables)
///     ```
///
/// 1. Call ``setup()`` to initiate all the necessary processes to start PhenixCore.
///     ```
///     core.setup()
///     ```
///
/// - Important: It's important to call ``setup()`` method before calling any other API methods,
/// otherwise all other API calls will result with an event ``Event/coreNotInitialized`` from
/// ``eventPublisher`` and nothing will happen.
public final class PhenixCore: PhenixCoreChannelProvider, PhenixCoreMessageProvider {

    /// Publisher's debounce time
    ///
    /// To not cause a rapid updated to the public publishers with lists, we are debouncing the events by the provided time.
    private static let publisherDebounceTime: DispatchQueue.SchedulerTimeType.Stride = .milliseconds(250)

    private let id: UUID
    private let queue: DispatchQueue
    private let publisherQueue: DispatchQueue

    // MARK: - Core Controllers
    private let controller: CoreController

    // MARK: - Subjects
    private let eventSubject = PassthroughSubject<Event, Error>()
    private let streamsSubject = CurrentValueSubject<[Stream], Never>([])
    private let membersSubject = CurrentValueSubject<[Member], Never>([])
    private let messagesSubject = CurrentValueSubject<[Message], Never>([])
    private let channelsSubject = CurrentValueSubject<[Channel], Never>([])
    private let memberCountSubject = CurrentValueSubject<Int, Never>(0)

    // MARK: - Publishers

    /// An event publisher which informs about various **PhenixCore** events.
    ///
    /// All of the public API methods, except ``setup()`` are asynchronous. So to
    /// be up-to-date with the result of the method call, this publisher will produce different
    /// kinds of events to the subscribers, so that the app could react to it appropriatly.
    ///
    /// For example,
    /// * When local media is initialized, it will produce ``Event/media(_:)``
    /// event with ``MediaEvent/mediaInitialized`` associated type instance.
    ///
    /// * Or if you start to publish a local media into a ``Room``, it will produce ``Event/room(_:)``
    /// event with ``Room/Event/roomPublished(alias:)`` associated type instance.
    ///
    /// For full list of possible events, please see ``Event``.
    public lazy var eventPublisher: AnyPublisher<Event, Error> = eventSubject
        .receive(on: publisherQueue)
        .share()
        .eraseToAnyPublisher()

    /// A message publisher, which provides a list of currently received chat messages from all joined ``Channel`` and ``Room`` instances.
    ///
    /// To receive the ``Channel`` or ``Room`` chat messages, first you need to join the approproate
    /// ``Channel`` or ``Room``. After that, you can observe the messages
    /// by calling ``subscribeToMessages(alias:configuration:)``.
    ///
    /// To unsubscribe from a messages, call ``unsubscribeMessages(alias:mimeType:)``.
    public lazy var messagesPublisher: AnyPublisher<[Message], Never> = messagesSubject
        .debounce(for: Self.publisherDebounceTime, scheduler: publisherQueue)
        .receive(on: publisherQueue)
        .eraseToAnyPublisher()

    /// A channel publisher, which provides a list of currently joined channels.
    ///
    /// This list gets populated when we successfully join a channel by calling ``joinToChannel(configuration:)``.
    /// The channel will be removed from the list when we call ``leave(alias:)``.
    public lazy var channelsPublisher: AnyPublisher<[Channel], Never> = channelsSubject
        .debounce(for: Self.publisherDebounceTime, scheduler: publisherQueue)
        .receive(on: publisherQueue)
        .eraseToAnyPublisher()

    /// A stream publisher, which provides a list of currently joined streams.
    ///
    /// This list gets populated when we successfully join a stream by calling ``joinToStream(configuration:)``.
    /// The stream will be removed from the list when we call ``leave(alias:)`` and provide the ``Stream/id``.
    public lazy var streamsPublisher: AnyPublisher<[Stream], Never> = streamsSubject
        .debounce(for: Self.publisherDebounceTime, scheduler: publisherQueue)
        .receive(on: publisherQueue)
        .eraseToAnyPublisher()

    /// A room members publisher, which provides a list of currently joined members in the joined room.
    ///
    /// When the room is left by calling ``leave(alias:)`` and providing the ``Room/alias``,
    /// all the members will be removed from this list.
    public lazy var membersPublisher: AnyPublisher<[Member], Never> = membersSubject
        .debounce(for: Self.publisherDebounceTime, scheduler: publisherQueue)
        .receive(on: publisherQueue)
        .eraseToAnyPublisher()

    /// A member count publisher for the currently joined room.
    ///
    /// If no room is joined, the member count will show **0**.
    public lazy var memberCountPublisher: AnyPublisher<Int, Never> = memberCountSubject
        .receive(on: publisherQueue)
        .eraseToAnyPublisher()

    // MARK: - Different parameters

    /// A list of currently joined ``Member`` instances in the joined ``Room`` instance.
    ///
    /// To be able to observe the list changes, subscribe to ``membersPublisher``.
    public var members: [Member] { membersSubject.value }

    /// A list of currently joined ``Stream`` instances.
    ///
    /// To be able to observe the list changes, subscribe to ``streamsPublisher``.
    public var streams: [Stream] { streamsSubject.value }

    /// A list of currently joined ``Channel`` instances.
    ///
    /// To be able to observe the list changes, subscribe to ``channelsPublisher``.
    public var channels: [Channel] { channelsSubject.value }

    /// A list of received ``Message`` instances from ``Channel`` or ``Room`` instances.
    ///
    /// To be able to observe the list changes, subscribe to ``messagesPublisher``
    public var messages: [Message] { messagesSubject.value }

    /// A PhenixCore configuration, which contains all the necessary information to successfully connect to the PhenixRTS services.
    public var configuration: Configuration { controller.configuration }

    // MARK: - Init

    /// A default initializer for the **PhenixCore**.
    /// - Parameters:
    ///   - configuration: A configuration struct, containing all the necessary information and tokens to successfully join the PhenixRTS services.
    ///   - publisherQueue: A queue, on which all of the public publisher events will be published. It is recomended to use the `DispatchQueue.main`.
    public convenience init(configuration: Configuration, publisherQueue: DispatchQueue = .main) {
        self.init(configuration: configuration, queue: nil, publisherQueue: publisherQueue)
    }

    /// An internal initializer for the **PhenixCore**.
    ///
    /// Provides an option to provide a custom internal dispatch queue, on which all of the **PhenixCore** functionality is called.
    /// - Parameters:
    ///   - configuration: A configuration struct, containing all the necessary information and tokens to successfully join the PhenixRTS services.
    ///   - queue: An internal queue, which is used to synchronize all of the internal functionality. If the provided value is `nil`, it will create it's own
    ///   internal queue for asynchronous **PhenixCore** usage.
    ///   - publisherQueue: A queue, on which all of the public publisher events will be published. It is recomended to use the `DispatchQueue.main`.
    internal convenience init(configuration: Configuration, queue: DispatchQueue?, publisherQueue: DispatchQueue) {
        let queue = queue ?? DispatchQueue(label: "Phenix.Core")
        let controller = CoreController(configuration: configuration, queue: queue, publisherQueue: publisherQueue)
        self.init(controller: controller, queue: queue, publisherQueue: publisherQueue)
    }

    private init(controller: CoreController, queue: DispatchQueue, publisherQueue: DispatchQueue) {
        self.id = UUID()
        self.queue = queue
        self.controller = controller
        self.publisherQueue = publisherQueue

        setupCoreEventPublisher()
    }

    /// Setups **PhenixCore** main processes.
    ///
    /// It can be called only once per each **PhenixCore** instance.
    ///
    /// By calling this method, all of the internal processes will be started
    /// and after successfully finished, all other public API methods will be able to work properly.
    ///
    /// - Note: This method is thread-safe and synchronous.
    public func setup() {
        queue.sync {
            guard controller.isInitialized == false else {
                return
            }

            controller.setup()
            setupCorePublishers()
        }
    }

    // MARK: - Channel

    /// Creates a new ``Channel``.
    ///
    /// - Note: This method produces ``Event/channel(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameter configuration: Channel configuration.
    public func createChannel(configuration: Channel.Configuration) {
        processAsync { [weak self] in
            self?.controller.createChannel(configuration: configuration)
        }
    }

    /// Joins to an existing ``Channel``.
    ///
    /// After successfully joining the ``Channel``, it will appear in ``channels``.
    ///
    /// To observe the channels list, use ``channelsPublisher``.
    ///
    /// If the channel does not exist, it will fail to join the it and will produce an error event.
    ///
    /// - Note: This method produces ``Event/channel(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameter configuration: Channel configuration.
    public func joinToChannel(configuration: Channel.Configuration) {
        processAsync { [weak self] in
            self?.controller.joinToChannel(configuration: configuration)
        }
    }

    /// Publishes local media to the  ``Channel``..
    ///
    /// If the channel does not exist, it will fail to publish the local media to the channel and produce an error event.
    ///
    /// - Note: This method produces ``Event/channel(_:)`` events in ``eventPublisher``.
    ///
    /// - Important: Before publishing, you must call ``setLocalMedia(enabled:configuration:)``
    /// with `enabled: true` at least once, to enable the local media.
    /// After that, wait for the `.media(.mediaInitialized)` event from
    /// the ``eventPublisher`` and then you can proceed with publishing.
    /// Without doing that first, you won't be able to publish local media.
    ///
    /// - Parameter configuration: Channel configuration.
    public func publishToChannel(configuration: Channel.Configuration) {
        processAsync { [weak self] in
            self?.controller.publishToChannel(configuration: configuration)
        }
    }

    /// Stops the local media publishing to the ``Channel``.
    public func stopPublishingToChannel() {
        processAsync { [weak self] in
            self?.controller.stopPublishingToChannel()
        }
    }

    /// Sets the ``Channel/isSelected`` value for a given `alias`, if possible.
    ///
    /// When the value changes, the ``Channel/selectionStatePublisher`` will publish a new value.
    ///
    /// - Parameters:
    ///   - alias: Channel alias.
    ///   - isSelected: New ``Channel/isSelected`` value.
    public func selectChannel(alias: String, isSelected: Bool) {
        processAsync { [weak self] in
            self?.controller.selectChannel(alias: alias, isSelected: isSelected)
        }
    }

    // MARK: - Streams

    /// Joins to an existing ``Stream``.
    ///
    /// After successfully joining the ``Stream``, it will appear in ``streams``.
    ///
    /// To observe the stream list, use ``streamsPublisher``.
    ///
    /// If the stream does not exist, it will fail to join and produce an error event.
    ///
    /// - Note: This method produces ``Event/stream(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameter configuration: Stream configuration.
    public func joinToStream(configuration: Stream.Configuration) {
        processAsync { [weak self] in
            self?.controller.joinToStream(configuration: configuration)
        }
    }

    /// Sets the ``Stream/isSelected`` value for a given `id`, if possible.
    ///
    /// When the value changes, the ``Stream/selectionStatePublisher`` will publish the new value.
    ///
    /// - Parameters:
    ///   - id: Stream id.
    ///   - isSelected: New ``Stream/isSelected`` value.
    public func selectStream(id: String, isSelected: Bool) {
        processAsync { [weak self] in
            self?.controller.selectStream(id: id, isSelected: isSelected)
        }
    }

    // MARK: - Room

    /// Creates a new ``Room``.
    ///
    /// - Note: This method produces ``Event/room(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameter configuration: Room configuration.
    public func createRoom(configuration: Room.Configuration) {
        processAsync { [weak self] in
            self?.controller.createRoom(configuration: configuration)
        }
    }

    /// Joins to an existing ``Room``.
    ///
    /// If the room does not exist, it will fail to join the room and produce an error event.
    ///
    /// - Note: This method produces ``Event/room(_:)`` events in ``eventPublisher``.
    ///
    /// - Important: Currently it is possible only to join **one** room at the time!
    /// To join other room, you first must leave the current one by calling ``leave(alias:)``.
    ///
    /// - Parameter configuration: Room configuration.
    public func joinToRoom(configuration: Room.Configuration) {
        processAsync { [weak self] in
            self?.controller.joinToRoom(configuration: configuration)
        }
    }

    /// Publishes local media to the ``Room``.
    ///
    /// If the room does not exist or you haven't joined the room first, it will fail to publish the local media and it will produce an error event.
    ///
    /// - Note: This method produces ``Event/room(_:)`` events in ``eventPublisher``.
    ///
    /// - Important: Before publishing, you must call ``setLocalMedia(enabled:configuration:)``
    /// with `enabled: true` at least once, to enable the local media.
    /// After that, wait for the `.media(.mediaInitialized)` event from
    /// the ``eventPublisher`` and then you can proceed with publishing.
    /// Without doing that first, you won't be able to publish local media.
    ///
    /// - Parameter configuration: Room configuration.
    public func publishToRoom(configuration: Room.Configuration) {
        processAsync { [weak self] in
            self?.controller.publishToRoom(configuration: configuration)
        }
    }

    /// Stops the local media publishing to the ``Room``.
    ///
    /// - Note: After stoping publishing,
    /// ``Member/role-swift.property`` will be automatically set to ``Member/Role-swift.enum/audience`` and
    /// ``Member/state-swift.property`` to ``Member/State-swift.enum/active``.
    public func stopPublishingToRoom() {
        processAsync { [weak self] in
            self?.controller.stopPublishingToRoom()
        }
    }

    // MARK: - Member

    /// Sets the ``Member/isSelected``  value for a given `id`, if possible.
    ///
    /// When the value changes,  the ``Member/selectionStatePublisher`` will publish the new value.
    ///
    /// - Parameters:
    ///   - id: Member id.
    ///   - isSelected: New ``Member/isSelected`` value.
    public func selectMember(_ id: Member.ID, isSelected: Bool) {
        processAsync { [weak self] in
            self?.controller.selectMember(id: id, isSelected: isSelected)
        }
    }

    /// Updates ``Member`` properties for a given `id`, if possible.
    ///
    /// - Parameters:
    ///   - id: Existing member id.
    ///   - role: New member role. If set as `nil`, the existing value won't be changed.
    ///   - state: New member state. If set as `nil`, the existing value won't be changed.
    ///   - name: New member name. If set as `nil`, the existing value won't be changed.
    public func updateMember(
        _ id: Member.ID,
        role: Member.Role? = nil,
        state: Member.State? = nil,
        name: String? = nil
    ) {
        processAsync { [weak self] in
            self?.controller.updateMember(id: id, role: role, state: state, name: name)
        }
    }

    /// Subscribe to all the member's media in the currently joined ``Room``.
    ///
    /// - Important: This method does not do anything, if the ``Room/Configuration/joinsSilently`` is set to `true`.
    /// In that case, when each member will appear in the room, app will automatically subscribe to its media.
    public func subscribeToRoomMembers() {
        processAsync { [weak self] in
            self?.controller.subscribeToRoomMembers()
        }
    }

    // MARK: - Message

    /// Subscribe for chat messages for a given `alias`, if possible.
    ///
    /// - Note: This method produces ``Event/message(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or  ``Room``  alias.
    ///   - configuration: Message configuration.
    public func subscribeToMessages(alias: String, configuration: Message.Configuration) {
        processAsync { [weak self] in
            self?.controller.subscribeToMessages(alias: alias, configuration: configuration)
        }
    }

    /// Unsubscribe from the chat messages for a given `alias`, if possible.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or  ``Room``  alias.
    ///   - mimeType: If mime type is provided, it will only unsubscribe for that specific mime type.
    ///   If not provided, then it will unsubscribe from all mime types. for the provided alias.
    public func unsubscribeMessages(alias: String, mimeType: String? = nil) {
        processAsync { [weak self] in
            self?.controller.unsubscribeMessages(alias: alias, mimeType: mimeType)
        }
    }

    /// Send a message for a given `alias`, if possible.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or  ``Room``  alias.
    ///   - message: Message to send.
    ///   - mimeType: Mime type, to which to send the message.
    public func sendMessage(alias: String, message: String, mimeType: String) {
        processAsync { [weak self] in
            self?.controller.sendMessage(alias: alias, message: message, mimeType: mimeType)
        }
    }

    // MARK: - Disposing

    /// Leave joined ``Channel``, ``Room`` or ``Stream``.
    ///
    /// While leaving, it will automatically unsubscribe for the messages for a given `alias`, if possible.
    ///
    /// - Note: This method produces ``Event`` events in ``eventPublisher``.
    ///
    /// - Parameter alias: ``Channel``, ``Room`` or ``Stream``  alias.
    public func leave(alias: String) {
        processAsync { [weak self] in
            self?.controller.leave(alias: alias)
        }
    }

    // MARK: - TimeShift

    /// Creates time-shift for a given `alias` starting at specific point in time.
    ///
    /// When the time-shift is created and ``Channel/timeShiftState`` is ``TimeShift/State/ready``,
    /// you can call ``playTimeShift(alias:)`` or ``playTimeShift(alias:loop:)`` to start playing.
    ///
    /// - Note: This method produces ``Event/timeShift(_:)``
    /// and ``Event/timeShiftCreationFailed`` events in ``eventPublisher``.
    ///
    /// - Important: ``TimeShift/PointInTime/timestamp(_:)`` works only with ``Channel``.
    ///
    /// ``TimeShift/PointInTime/seek(offset:from:)`` works only with ``Stream``.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or ``Stream`` alias.
    ///   - pointInTime: Represents a specific point in time ``TimeShift/PointInTime/timestamp(_:)`` when to start time-shift
    ///   or a relative point in time ``TimeShift/PointInTime/seek(offset:from:)`` which calculates time-shift start-date and starts from there.
    public func createTimeShift(alias: String, on pointInTime: TimeShift.PointInTime) {
        processAsync { [weak self] in
            self?.controller.createTimeShift(alias: alias, on: pointInTime)
        }
    }

    /// Starts playing time-shift for a given `alias`, if possible.
    ///
    /// - Note: This method produces ``Event/timeShift(_:)``
    /// and ``Event/timeShiftCreationFailed`` events in ``eventPublisher``.
    ///
    /// - Parameter alias: ``Channel`` or ``Stream`` alias.
    public func playTimeShift(alias: String) {
        processAsync { [weak self] in
            self?.controller.playTimeShift(alias: alias)
        }
    }

    /// Starts playing time-shift for a specific `loop` duration for a given `alias`, if possible.
    ///
    /// When the duration will end, the time shift will start playing video from the beginning.
    ///
    /// - Note: This method produces ``Event/timeShift(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or ``Stream`` alias.
    ///   - duration: Loop duration in seconds.
    public func playTimeShift(alias: String, loop duration: TimeInterval) {
        processAsync { [weak self] in
            self?.controller.playTimeShift(alias: alias, loop: duration)
        }
    }

    /// Seeks time-shift playback by an `offset` for a given `alias`, if possible.
    ///
    /// Before starting seeking, time-shift will pause the video. When the time-shift will finish seeking,
    /// it will change the ``Channel/timeShiftState`` or ``Stream/timeShiftState``
    /// to ``TimeShift/State/seekingSucceeded``.
    /// At that point you should call ``playTimeShift(alias:)`` to start plaing the video again.
    ///
    /// - Note: This method produces ``Event/timeShift(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or ``Stream`` alias.
    ///   - offset: Time interval for seeking, provided in seconds.
    public func seekTimeShift(alias: String, offset: TimeInterval) {
        processAsync { [weak self] in
            self?.controller.seekTimeShift(alias: alias, offset: offset)
        }
    }

    /// Pauses time-shift playback for a given `alias`, if possible.
    ///
    /// - Note: This method produces ``Event/timeShift(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameter alias: ``Channel`` or ``Stream`` alias.
    public func pauseTimeShift(alias: String) {
        processAsync { [weak self] in
            self?.controller.pauseTimeShift(alias: alias)
        }
    }

    /// Stops time-shift playback for a given `alias`, if possible.
    ///
    /// - Note: This method produces ``Event/timeShift(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameter alias: ``Channel`` or ``Stream`` alias.
    public func stopTimeShift(alias: String) {
        processAsync { [weak self] in
            self?.controller.stopTimeShift(alias: alias)
        }
    }

    // MARK: - Bandwidth

    /// Limits the bandwidth for a given `alias` with the given `bandwidth` value.
    ///
    /// Provide **0** for `bandwidth` to set the limit to **unlimited**, otherwise a positive value is required.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or ``Stream`` alias.
    ///   - bandwidth: Bandwidth value
    ///
    ///     For example:
    ///     - Ultra Low Definition (ULD) is a value of: 80_000
    ///     - Ultra High Definition (UHD) is a value of: 8_500_000
    public func setBandwidthLimitation(alias: String, bandwidth: UInt64) {
        processAsync { [weak self] in
            self?.controller.setBandwidthLimitation(alias: alias, bandwidth: bandwidth)
        }
    }

    /// Removes any bandwidth limitation for a given `alias`.
    ///
    /// - Parameter alias: ``Channel`` or ``Stream`` alias.
    public func removeBandwidthLimitation(alias: String) {
        processAsync { [weak self] in
            self?.controller.removeBandwidthLimitation(alias: alias)
        }
    }

    // MARK: - Remote Media

    /// Renders a remote ``Channel``, ``Member`` or ``Stream`` video for a given `alias` on a given layer.
    ///
    /// If the provided layer is `nil`, the rendering for a given `alias` will be stopped.
    ///
    /// - Parameters:
    ///   - alias: ``Channel``, ``Member`` or ``Stream`` alias.
    ///   - layer: Layer on which to render the video.
    public func renderVideo(alias: String, layer: CALayer?) {
        processAsync { [weak self] in
            self?.controller.renderVideo(alias: alias, layer: layer)
        }
    }

    /// Renders a remote ``Channel``, ``Member`` or ``Stream`` video for a given `alias` on a given layer.
    ///
    /// If the provided layer is `nil`, the rendering for a given `alias` will be stopped.
    ///
    /// - Note: This method uses a different technology under-the-hood for video rendering, called `frame-ready`.
    ///
    /// - Parameters:
    ///   - alias: ``Channel``, ``Member`` or ``Stream`` alias.
    ///   - layer: Layer on which to render the video.
    public func renderThumbnailVideo(alias: String, layer: CALayer?) {
        processAsync { [weak self] in
            self?.controller.renderThumbnailVideo(alias: alias, layer: layer)
        }
    }

    /// Toggles the audio state for a given `alias`, if possible.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or ``Stream`` alias.
    ///   - enabled: If set to `true`, then audio will be enabled, otherwise - disabled.
    public func setAudioEnabled(alias: String, enabled: Bool) {
        processAsync { [weak self] in
            self?.controller.setAudioEnabled(alias: alias, enabled: enabled)
        }
    }

    /// Toggles the video state for a given `alias`, if possible.
    ///
    /// - Parameters:
    ///   - alias: ``Channel`` or ``Stream`` alias.
    ///   - enabled: If set to `true`, then video will be enabled, otherwise - disabled.
    public func setVideoEnabled(alias: String, enabled: Bool) {
        processAsync { [weak self] in
            self?.controller.setVideoEnabled(alias: alias, enabled: enabled)
        }
    }

    // MARK: - Local Media

    /// Renders the video of local media on a given layer.
    ///
    /// If the provided layer is `nil`, the rendering will be stopped.
    ///
    /// - Parameter layer: Layer on which to render the video.
    public func previewVideo(layer: CALayer?) {
        processAsync { [weak self] in
            self?.controller.previewOnSurface(layer: layer)
        }
    }

    /// Renders the video of local media on a given layer.
    ///
    /// If the provided layer is `nil`, the rendering will be stopped.
    ///
    /// - Note: This method uses a different technology under-the-hood for video rendering, called `frame-ready`.
    ///
    /// - Parameter layer: Layer on which to render the video.
    public func previewThumbnailVideo(layer: CALayer?) {
        processAsync { [weak self] in
            self?.controller.previewOnImage(layer: layer)
        }
    }

    /// Toggles the audio state of the local media.
    ///
    /// - Parameter enabled: If set to `true`, then audio will be enabled, otherwise - disabled.
    public func setSelfAudioEnabled(enabled: Bool) {
        processAsync { [weak self] in
            self?.controller.setSelfAudioEnabled(enabled: enabled)
        }
    }

    /// Toggles the video state of the local media.
    ///
    /// - Parameter enabled: If set to `true`, then video will be enabled, otherwise - disabled.
    public func setSelfVideoEnabled(enabled: Bool) {
        processAsync { [weak self] in
            self?.controller.setSelfVideoEnabled(enabled: enabled)
        }
    }

    /// Enables or disables the local media (camera and microphone of the device).
    ///
    /// It is necessary to enable local media if the user wants to publish the content (like video or audio) in the ``Channel`` or ``Room`` alias.
    ///
    /// - Note: This method produces ``Event/media(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameters:
    ///   - enabled: If set to `true`, then enables local media, otherwise - disables.
    ///   - configuration: Local media configuration.
    public func setLocalMedia(enabled: Bool, configuration: PhenixCore.MediaConfiguration = .default) {
        processAsync { [weak self] in
            self?.controller.setLocalMedia(enabled: enabled, configuration: configuration)
        }
    }

    /// Updates local media configuration.
    ///
    /// - Note: This method produces ``Event/media(_:)`` events in ``eventPublisher``.
    ///
    /// - Parameter configuration: Local media configuration.
    public func updateLocalMedia(_ configuration: PhenixCore.MediaConfiguration) {
        processAsync { [weak self] in
            self?.controller.updateLocalMedia(configuration)
        }
    }

    /// Toggles, which camera is used to publish local video.
    ///
    /// It toggles from front-camera to back-camera.
    ///
    /// - Note: This method produces ``Event/media(_:)`` events in ``eventPublisher``.
    public func flipCamera() {
        processAsync { [weak self] in
            self?.controller.flipCamera()
        }
    }

    // MARK: - Private methods

    private func processAsync(with handler: @escaping () -> Void) {
        queue.async { handler() }
    }

    private func setupCoreEventPublisher() {
        controller.eventPublisher.receive(subscriber: AnySubscriber(eventSubject))
    }

    private func setupCorePublishers() {
        controller.streamsPublisher.receive(subscriber: AnySubscriber(streamsSubject))
        controller.membersPublisher.receive(subscriber: AnySubscriber(membersSubject))
        controller.messagesPublisher.receive(subscriber: AnySubscriber(messagesSubject))
        controller.channelsPublisher.receive(subscriber: AnySubscriber(channelsSubject))
        controller.memberCountPublisher.receive(subscriber: AnySubscriber(memberCountSubject))
    }
}

// MARK: - Equatable
extension PhenixCore: Equatable {
    public static func == (lhs: PhenixCore, rhs: PhenixCore) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - PhenixCoreDebuggable
extension PhenixCore: PhenixCoreDebuggable {
    /// Collects logs, generated by the PhenixSdk
    ///
    /// - Parameter completion: Completion handler is called when the log collection finishes.
    /// String property will contain the collected logs. Called on **DispatchQueue.main** thread.
    public func collectLogs(completion: @escaping (String?) -> Void) {
        processAsync { [weak self] in
            self?.controller.collectLogs(completion: completion)
        }
    }
}
