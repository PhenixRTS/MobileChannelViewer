//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

public extension PhenixCore.Member {
    enum Role: CustomStringConvertible {
        case moderator, audience, participant, presenter

        public var description: String {
            switch self {
            case .moderator:
                return "Moderator"
            case .audience:
                return "Audience"
            case .participant:
                return "Participant"
            case .presenter:
                return "Presenter"
            }
        }

        var phenixMemberRole: PhenixMemberRole {
            switch self {
            case .moderator:
                return .moderator
            case .audience:
                return .audience
            case .participant:
                return .participant
            case .presenter:
                return .presenter
            }
        }

        init?(phenixMemberRole: PhenixMemberRole) {
            switch phenixMemberRole {
            case .moderator:
                self = .moderator
            case .audience:
                self = .audience
            case .participant:
                self = .participant
            case .presenter:
                self = .presenter
            @unknown default:
                return nil
            }
        }
    }
}
