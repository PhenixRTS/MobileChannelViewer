//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log

extension OSLog {
    convenience init(identifier: String = #fileID) {
        // swiftlint:disable:next force_unwrapping
        self.init(subsystem: Bundle.main.bundleIdentifier!, category: "Phenix.Core.\(identifier)")
    }
}
