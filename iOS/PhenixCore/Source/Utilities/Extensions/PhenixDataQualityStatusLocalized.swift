//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk

extension PhenixDataQualityStatus: CustomStringConvertible {
    public var description: String {
        switch self {
        case .noData:
            return "No-Data"
        case .all:
            return "All"
        case .audioOnly:
            return "Audio-Only"
        @unknown default:
            return "Unknown"
        }
    }
}
