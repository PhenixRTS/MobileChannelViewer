//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

extension PhenixCore.TimeShift {
    public enum State: CustomStringConvertible {
        case idle, starting, ready, playing, paused, seeking, seekingSucceeded, ended, failed

        public var description: String {
            switch self {
            case .idle:
                return "Idle"
            case .starting:
                return "Starting"
            case .ready:
                return "Ready"
            case .playing:
                return "Playing"
            case .paused:
                return "Paused"
            case .seeking:
                return "Seeking"
            case .seekingSucceeded:
                return "SeekingSucceeded"
            case .ended:
                return "Ended"
            case .failed:
                return "Failed"
            }
        }
    }
}
