//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Stream {
    enum Event {
        case streamJoining(id: String)
        case streamJoiningFailed(id: String, error: Error)
        case streamJoined(id: String)
    }
}
