//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore {
    /// A possible **PhenixCore** events produced by the ``eventPublisher``
    enum Event {
        case room(Room.Event)
        case channel(Channel.Event)
        case message(Message.Event)
        case stream(Stream.Event)
        case media(MediaEvent)
        case timeShift(TimeShift.Event)
        case coreInitialized
        case coreNotInitialized
        case timeShiftCreationFailed
    }
}
