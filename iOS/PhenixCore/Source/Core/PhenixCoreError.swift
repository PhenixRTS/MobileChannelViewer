//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore {
    enum Error: Swift.Error {
        case unrecoverableError(description: String?)
        case localMediaInitializationFailed
    }
}
