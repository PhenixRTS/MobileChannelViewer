//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation

public protocol PhenixCoreChannelProvider: AnyObject {
    var channelsPublisher: AnyPublisher<[PhenixCore.Channel], Never> { get }
}
