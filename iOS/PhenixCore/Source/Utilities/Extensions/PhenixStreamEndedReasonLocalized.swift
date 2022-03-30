//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk

extension PhenixStreamEndedReason: CustomStringConvertible {
    public var description: String {
        switch self {
        case .appBackground:
            return "PhenixStreamEndedReason.appBackground"
        case .capacity:
            return "PhenixStreamEndedReason.capacity"
        case .censored:
            return "PhenixStreamEndedReason.censored"
        case .custom:
            return "PhenixStreamEndedReason.custom"
        case .ended:
            return "PhenixStreamEndedReason.ended"
        case .failed:
            return "PhenixStreamEndedReason.failed"
        case .maintenance:
            return "PhenixStreamEndedReason.maintenance"
        case .overload:
            return "PhenixStreamEndedReason.overload"
        case .pCastStop:
            return "PhenixStreamEndedReason.pCastStop"
        @unknown default:
            return "Unknown"
        }
    }
}


