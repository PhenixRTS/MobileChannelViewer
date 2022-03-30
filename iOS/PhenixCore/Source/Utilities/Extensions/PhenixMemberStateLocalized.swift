//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

extension PhenixMemberState: CustomStringConvertible {
    public var description: String {
        switch self {
        case .active:
            return "Active"
        case .handRaised:
            return "HandRaised"
        case .inactive:
            return "Inactive"
        case .offline:
            return "Offline"
        case .passive:
            return "Passive"
        @unknown default:
            return "Unknown"
        }
    }
}
