//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

extension PhenixCore {
    final class MessageServiceContainer {
        private let lastChatMessageDisposable: PhenixDisposable
        let service: PhenixRoomChatService

        init(service: PhenixRoomChatService, lastChatMessageDisposable: PhenixDisposable) {
            self.service = service
            self.lastChatMessageDisposable = lastChatMessageDisposable
        }
    }
}
