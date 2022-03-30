//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

protocol MemberUpdater: AnyObject {
    /// Send a signal to all member list subscribers, that the list or list item properties were changes, meaning that the list should be re-evaluated.
    func memberDidUpdate()
}
