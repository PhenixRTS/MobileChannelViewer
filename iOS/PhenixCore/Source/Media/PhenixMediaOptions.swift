//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore {
    enum MediaOptions {
        public enum CameraFacingMode: CaseIterable {
            case front, rear

            public var value: PhenixFacingMode {
                switch self {
                case .front:
                    return .user
                case .rear:
                    return .environment
                }
            }

            public mutating func toggle() {
                switch self {
                case .front:
                    self = .rear
                case .rear:
                    self = .front
                }
            }
        }

        public struct FrameRate: RawRepresentable, Equatable {
            public var rawValue: Double

            public init?(rawValue: Double) {
                self.rawValue = rawValue
            }

            public static let fps15 = FrameRate(rawValue: 15)!
            public static let fps30 = FrameRate(rawValue: 30)!
        }

        public enum AudioEchoCancellation: CaseIterable {
            case automatic, on, off

            public var value: PhenixAudioEchoCancelationMode {
                switch self {
                case .automatic:
                    return .automatic
                case .on:
                    return .on
                case .off:
                    return .off
                }
            }
        }
    }
}
