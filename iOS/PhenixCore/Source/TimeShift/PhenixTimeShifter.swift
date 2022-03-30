//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk

protocol PhenixTimeShifter {
    var renderer: PhenixRenderer? { get }

    func subscribe(_ timeShift: PhenixCore.TimeShift)
}
