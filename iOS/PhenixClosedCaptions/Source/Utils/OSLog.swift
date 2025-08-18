//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log

extension OSLog {
    // swiftlint:disable force_unwrapping
    private static var subsystem = Bundle.main.bundleIdentifier!

    static let service = OSLog(subsystem: subsystem, category: "Phenix.Core.ClosedCaptionsService")
    static let containerView = OSLog(subsystem: subsystem, category: "Phenix.Core.ClosedCaptionsView")
}
