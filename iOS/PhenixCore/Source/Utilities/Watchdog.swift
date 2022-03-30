//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

final class Watchdog {
    private let queue: DispatchQueue
    private let workItem: DispatchWorkItem
    private let timeInterval: TimeInterval

    init(timeInterval: TimeInterval, queue: DispatchQueue = .main, afterTimePasses: @escaping () -> Void) {
        self.queue = queue
        self.timeInterval = timeInterval
        workItem = DispatchWorkItem(block: afterTimePasses)
    }

    func start() {
        queue.asyncAfter(deadline: .now() + timeInterval, execute: workItem)
    }

    func cancel() {
        workItem.cancel()
    }

    deinit {
        cancel()
    }
}
