//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

final class MemberStreamVideoStateProvider {
    private static let logger = OSLog(identifier: "MemberStreamVideoStateProvider")

    private let queue: DispatchQueue
    private let stream: PhenixStream
    private let associatedMemberID: String
    private var disposable: PhenixDisposable?

    var stateChangeHandler: ((Bool) -> Void)?

    init(associatedMemberID: String, stream: PhenixStream, queue: DispatchQueue) {
        self.queue = queue
        self.stream = stream
        self.associatedMemberID = associatedMemberID
    }

    func observeState() {
        os_log(.debug, log: Self.logger, "%{private}s, Observe video state changes", associatedMemberID)

        disposable = stream
            .getObservableVideoState()
            .subscribe { [weak self] changes in
                self?.videoStateDidChange(changes)
            }
    }
}

// MARK: - CustomStringConvertible
extension MemberStreamVideoStateProvider: CustomStringConvertible {
    var description: String {
        "MemberStreamVideoStateProvider(alias: \(String(describing: associatedMemberID)))"
    }
}

// MARK: - Disposable
extension MemberStreamVideoStateProvider: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose", associatedMemberID)

        disposable = nil
        stateChangeHandler = nil
    }
}

// MARK: - Observable callbacks
private extension MemberStreamVideoStateProvider {
    func videoStateDidChange(_ changes: PhenixObservableChange<NSNumber>?) {
        queue.async { [weak self] in
            guard let value = changes?.value else { return }
            guard let state = PhenixTrackState(rawValue: Int(truncating: value)) else { return }

            self?.stateChangeHandler?(state == .enabled)
        }
    }
}
