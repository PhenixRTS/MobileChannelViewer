//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

extension PhenixMemberRole: CustomStringConvertible {
    public var description: String {
        switch self {
        case .audience:
            return "Audience"
        case .moderator:
            return "Moderator"
        case .participant:
            return "Participant"
        case .presenter:
            return "Presenter"
        @unknown default:
            return "Unknown"
        }
    }
}
