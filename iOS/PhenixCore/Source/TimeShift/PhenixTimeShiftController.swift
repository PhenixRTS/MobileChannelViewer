//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import os.log
import PhenixSdk

extension PhenixCore {
    class TimeShiftController {
        private static let logger = OSLog(identifier: "TimeShiftController")

        private let queue: DispatchQueue

        // MARK: - Subjects
        private var eventSubject = PassthroughSubject<TimeShift.Event, Never>()
        private var timeShiftsSubject = CurrentValueSubject<[TimeShift], Never>([])

        // MARK: - Publishers
        lazy var eventPublisher = eventSubject.eraseToAnyPublisher()
        lazy var timeShiftsPublisher = timeShiftsSubject.eraseToAnyPublisher()

        init(queue: DispatchQueue) {
            self.queue = queue
        }

        func createTimeShift(
            alias: String,
            on pointInTime: PhenixCore.TimeShift.PointInTime,
            with renderer: PhenixRenderer
        ) -> TimeShift {
            disposeTimeShift(alias: alias)

            let maxRetryCount: Int

            switch pointInTime {
            case .seek(_, _):
                maxRetryCount = 3

            case .timestamp(let startDate):
                let interval = abs(startDate.distance(to: .init()))
                maxRetryCount = Int(interval / PhenixCore.TimeShift.retryDelayTimeInterval)
            }

            let timeShift = PhenixCore.TimeShift(
                renderer: renderer,
                pointInTime: pointInTime,
                maxRetryCount: maxRetryCount,
                associatedAlias: alias,
                queue: queue
            )

            timeShift.eventPublisher.receive(subscriber: AnySubscriber(eventSubject))

            timeShiftsSubject.value.append(timeShift)

            timeShift.setup()

            return timeShift
        }

        func disposeTimeShift(alias: String) {
            timeShift(withAlias: alias)?.dispose()
            timeShiftsSubject.value.removeAll(where: { $0.alias == alias })
        }

        func playTimeShift(alias: String) {
            timeShift(withAlias: alias)?.play()
        }

        func playTimeShift(alias: String, loop duration: TimeInterval) {
            timeShift(withAlias: alias)?.play(loop: duration)
        }

        func seekTimeShift(alias: String, offset: TimeInterval) {
            timeShift(withAlias: alias)?.seek(offset: offset)
        }

        func pauseTimeShift(alias: String) {
            timeShift(withAlias: alias)?.pause()
        }

        func stopTimeShift(alias: String) {
            timeShift(withAlias: alias)?.stop()
        }

        func setBandwidthLimitation(_ bandwidth: UInt64, alias: String) {
            timeShift(withAlias: alias)?.setBandwidthLimitation(bandwidth)
        }

        func removeBandwidthLimitation(alias: String) {
            timeShift(withAlias: alias)?.removeBandwidthLimitation()
        }

        func removeTimeShift(alias: String) {
            if let index = timeShiftsSubject.value.firstIndex(where: { $0.alias == alias }) {
                os_log(.debug, log: Self.logger, "%{private}s, Remove timeShift", alias)
                timeShiftsSubject.value
                    .remove(at: index)
                    .dispose()
            }
        }

        // MARK: - Private methods

        private func timeShift(withAlias alias: String) -> TimeShift? {
            timeShiftsSubject.value.first(where: { $0.alias == alias })
        }
    }
}
