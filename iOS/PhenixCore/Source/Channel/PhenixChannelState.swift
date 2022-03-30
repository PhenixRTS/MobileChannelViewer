//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public extension PhenixCore.Channel {
    enum State {
        case streaming, noStream, joining, offline
    }
}
