//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public protocol PhenixClosedCaptionsControllerDelegate: AnyObject {
    func closedCaptionsController(_ controller: PhenixClosedCaptionsController, didReceive message: PhenixClosedCaptionsMessage)
}
