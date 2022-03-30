//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

/// A PhenixSdk inspector, which provides information about the SDK version.
struct SDKInspector {
    private var bundle: Bundle? = {
        // Locate the PhenixSdk framework by its bundle identifier.
        Bundle(identifier: "com.phenixrts.framework")
    }()

    /// Framework version
    var version: String? { bundle?.appVersion }

    /// Framework build version
    var buildVersion: String? { bundle?.appBuildVersion }
}
