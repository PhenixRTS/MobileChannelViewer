//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Member {
    enum Volume: Comparable {
        case volume9
        case volume8
        case volume7
        case volume6
        case volume5
        case volume4
        case volume3
        case volume2
        case volume1
        case volume0

        init(rawValue: Double) {
            switch rawValue {
            case let value where value < -79:
                self = .volume0
            case (-79 ..< -70):
                self = .volume1
            case (-70 ..< -55):
                self = .volume2
            case (-55 ..< -45):
                self = .volume3
            case (-45 ..< -40):
                self = .volume4
            case (-40 ..< -35):
                self = .volume5
            case (-35 ..< -30):
                self = .volume6
            case (-30 ..< -25):
                self = .volume7
            case (-25 ..< -20):
                self = .volume8
            case let value where value >= -20:
                self = .volume9
            default:
                self = .volume0
            }
        }
    }
}
