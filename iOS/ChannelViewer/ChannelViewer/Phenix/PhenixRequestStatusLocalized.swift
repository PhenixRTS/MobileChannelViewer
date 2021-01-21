//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk

extension PhenixRequestStatus: CustomStringConvertible {
    public var description: String {
        switch self {
        case .ok:               return "PhenixRequestStatus.ok"
        case .noStreamPlaying:  return "PhenixRequestStatus.noStreamPlaying"
        case .badRequest:       return "PhenixRequestStatus.badRequest"
        case .unauthorized:     return "PhenixRequestStatus.unauthorized"
        case .conflict:         return "PhenixRequestStatus.conflict"
        case .gone:             return "PhenixRequestStatus.gone"
        case .notInitialized:   return "PhenixRequestStatus.notInitialized"
        case .notStarted:       return "PhenixRequestStatus.notStarted"
        case .rateLimited:      return "PhenixRequestStatus.rateLimited"
        case .upgradeRequired:  return "PhenixRequestStatus.upgradeRequired"
        case .failed:           return "PhenixRequestStatus.failed"
        case .capacity:         return "PhenixRequestStatus.capacity"
        case .timeout:          return "PhenixRequestStatus.timeout"
        case .notReady:         return "PhenixRequestStatus.notReady"

        default:                return "Unknown status: \(rawValue)"
        }
    }
}
