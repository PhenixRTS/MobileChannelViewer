//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

extension PhenixCore {
    public enum StreamToken {
        case audio(String)
        case video(String)
        case universal(String)

        var value: String {
            switch self {
            case .audio(let string), .video(let string), .universal(let string):
                return string
            }
        }
    }
}
