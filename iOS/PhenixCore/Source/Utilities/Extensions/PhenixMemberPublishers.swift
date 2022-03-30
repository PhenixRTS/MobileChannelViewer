//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation
import PhenixSdk

// MARK: - Combine's Publisher extension

extension PhenixMember {
    var streamsPublisher: AnyPublisher<[PhenixStream], Never> {
        PhenixObservablePublisher(observer: self.getObservableStreams()).eraseToAnyPublisher()
    }
}
