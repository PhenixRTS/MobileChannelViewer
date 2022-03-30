//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.TimeShift {
    enum PointInTime {
        /// Provides an offset in seconds from the stream origin point.
        ///
        /// An example would be to create a time-shift with 40 second offset
        /// after the beginning of the stream: `.seek(offset: 40, from: .beginning)`
        case seek(offset: TimeInterval, from: Origin)

        /// Provides a specific date-time from where to create the time-shift.
        ///
        /// The timestamp date must be in the UTC timezone.
        case timestamp(Date)
    }
}

public extension PhenixCore.TimeShift.PointInTime {
    enum Origin {
        case beginning
        case current
        case end

        var phenixSeekOrigin: PhenixSeekOrigin {
            switch self {
            case .beginning:
                return .beginning
            case .current:
                return .current
            case .end:
                return .end
            }
        }
    }
}

extension PhenixCore.TimeShift.PointInTime: CustomStringConvertible {
    public var description: String {
        switch self {
        case let .seek(offset, from):
            return "TimeShift.PointInTime(offset: \(offset), from: \(from))"
        case let .timestamp(date):
            return "TimeShift.PointInTime(timestamp: \(date))"
        }
    }
}
