//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public protocol PhenixClosedCaptionsServiceDelegate: AnyObject {
    func closedCaptionsService(_ service: PhenixClosedCaptionsService, didReceive message: PhenixClosedCaptionsMessage)
}
