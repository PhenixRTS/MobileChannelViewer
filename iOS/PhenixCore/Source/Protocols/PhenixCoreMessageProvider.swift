//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation

public protocol PhenixCoreMessageProvider: AnyObject {
    var messagesPublisher: AnyPublisher<[PhenixCore.Message], Never> { get }

    func subscribeToMessages(alias: String, configuration: PhenixCore.Message.Configuration)
}
