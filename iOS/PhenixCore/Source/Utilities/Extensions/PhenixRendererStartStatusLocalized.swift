//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk

extension PhenixRendererStartStatus: CustomStringConvertible {
    public var description: String {
        switch self {
        case .ok:
            return "PhenixRendererStartStatus.ok"
        case .badRequest:
            return "PhenixRendererStartStatus.badRequest"
        case .conflict:
            return "PhenixRendererStartStatus.conflict"
        case .failed:
            return "PhenixRendererStartStatus.failed"
        case .timeout:
            return "PhenixRendererStartStatus.timeout"
        @unknown default:
            return "Unknown status: \(rawValue)"
        }
    }
}

