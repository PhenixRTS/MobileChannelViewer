//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore {
    enum AspectRatio {
        case fit, fill

        var phenixAspectRatio: PhenixAspectRatioMode {
            switch self {
            case .fit:
                return .letterbox
            case .fill:
                return .fill
            }
        }

        var videoGravity: AVLayerVideoGravity {
            switch self {
            case .fit:
                return .resizeAspect
            case .fill:
                return .resizeAspectFill
            }
        }
    }
}
