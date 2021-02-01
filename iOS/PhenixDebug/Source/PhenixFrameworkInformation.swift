//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

struct PhenixFrameworkInformation {
    private var bundle: Bundle? = {
        // Locate the PhenixSdk framework by its bundle identifier.
        Bundle(identifier: "com.phenixrts.framework")
    }()

    /// Framework version
    var version: String? { bundle?.appVersion }

    /// Framework build version
    var buildVersion: String? { bundle?.appBuildVersion }
}
