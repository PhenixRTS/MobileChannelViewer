//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.TimeShift {
    enum Event {
        case timeShiftFailed(alias: String, error: Error)
    }
}
