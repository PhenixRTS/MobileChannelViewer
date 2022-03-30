//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public protocol PhenixCoreDebuggable: AnyObject {
    func collectLogs(completion: @escaping (String?) -> Void)
}
