//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore {
    enum MediaEvent {
        case mediaNotInitialized
        case mediaInitializing
        case mediaInitializationFailed(error: MediaError)
        case mediaInitialized
        case mediaConfigurationUpdated
        case mediaConfigurationUpdateFailed(error: MediaError)
    }
}
