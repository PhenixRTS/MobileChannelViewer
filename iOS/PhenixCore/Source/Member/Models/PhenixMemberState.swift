//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.Member {
    enum State: CustomStringConvertible {
        case active, handRaised

        public var description: String {
            switch self {
            case .active:
                return "Active"
            case .handRaised:
                return "HandRaised"
            }
        }

        var phenixMemberState: PhenixMemberState {
            switch self {
            case .active:
                return .active
            case .handRaised:
                return .handRaised
            }
        }

        init(phenixMemberState: PhenixMemberState) {
            switch phenixMemberState {
            case .active:
                self = .active
            case .handRaised:
                self = .handRaised
            default:
                self = .active
            }
        }
    }
}
