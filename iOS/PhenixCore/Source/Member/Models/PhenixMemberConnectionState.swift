//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Member {
    enum ConnectionState: CustomStringConvertible {
        case active, away, pending, removed

        public var description: String {
            switch self {
            case .active:
                return "Active"
            case .away:
                return "Away"
            case .pending:
                return "Pending"
            case .removed:
                return "Removed"
            }
        }
    }
}
