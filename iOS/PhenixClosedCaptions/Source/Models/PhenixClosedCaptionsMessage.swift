//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public struct PhenixClosedCaptionsMessage: Codable {
    public var serviceId: String
    public var windowIndex: UInt
    public var windowUpdate: PhenixWindowUpdate?
    public var textUpdates: [PhenixTextUpdate]
}
