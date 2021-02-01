//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

extension Bundle {
    var appVersion: String? { self.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String }
    var appBuildVersion: String? { self.object(forInfoDictionaryKey: "CFBundleVersion") as? String }
}
