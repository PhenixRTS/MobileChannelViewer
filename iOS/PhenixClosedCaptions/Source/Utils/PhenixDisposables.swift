//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk

internal extension PhenixDisposable {
    func append(to sequence: inout [PhenixDisposable]) {
        sequence.append(self)
    }
}
