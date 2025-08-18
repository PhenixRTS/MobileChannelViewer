//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

extension Bundle {
    var appVersion: String? {
        object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
    }

    var appBuildVersion: String? {
        object(forInfoDictionaryKey: "CFBundleVersion") as? String
    }
}
