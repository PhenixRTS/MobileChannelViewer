//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import os.log
import PhenixSdk

final class MemberStreamAudioStateProvider {
    private static let logger = OSLog(identifier: "MemberStreamAudioStateProvider")

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
        os_log(.debug, log: Self.logger, "%{private}s, Observe audio state changes", associatedMemberID)

        disposable = stream
            .getObservableAudioState()
            .subscribe { [weak self] changes in
                self?.audioStateDidChange(changes)
            }
    }
}

// MARK: - CustomStringConvertible
extension MemberStreamAudioStateProvider: CustomStringConvertible {
    var description: String {
        "MemberStreamAudioStateProvider(alias: \(String(describing: associatedMemberID)))"
    }
}

// MARK: - Disposable
extension MemberStreamAudioStateProvider: Disposable {
    func dispose() {
        os_log(.debug, log: Self.logger, "%{private}s, Dispose", associatedMemberID)

        disposable = nil
        stateChangeHandler = nil
    }
}

// MARK: - Observable callbacks
private extension MemberStreamAudioStateProvider {
    func audioStateDidChange(_ changes: PhenixObservableChange<NSNumber>?) {
        queue.async { [weak self] in
            guard let value = changes?.value else { return }
            guard let state = PhenixTrackState(rawValue: Int(truncating: value)) else { return }

            self?.stateChangeHandler?(state == .enabled)
        }
    }
}
