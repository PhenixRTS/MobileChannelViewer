//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

extension CATransaction {
    /// Execute code without triggering the implicit animations
    /// - Parameter handler: Provided code to execute synchronously
    static func withoutAnimations(handler: () -> Void) {
        begin()
        setDisableActions(true)
        handler()
        commit()
    }
}
