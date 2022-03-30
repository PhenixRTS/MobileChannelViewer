//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import Foundation

class PhenixMediaInterruptionService {
    private let notificationCenter: NotificationCenter
    private let isVideoEnabled: () -> Bool

    private var cancellables = Set<AnyCancellable>()
    private var suggestedVideoStateSubject = PassthroughSubject<SuggestedVideoState, Never>()

    private var wasVideoEnabled: Bool

    public lazy var suggestedVideoStatePublisher = suggestedVideoStateSubject.eraseToAnyPublisher()

    init(notificationCenter: NotificationCenter = .default, isVideoEnabled: @escaping () -> Bool) {
        self.isVideoEnabled = isVideoEnabled
        self.wasVideoEnabled = false
        self.notificationCenter = notificationCenter
    }

    func start() {
        notificationCenter
            .publisher(for: UIApplication.willResignActiveNotification, object: nil)
            .sink { [weak self] _ in
                guard let self = self else {
                    return
                }

                let isVideoEnabled = self.isVideoEnabled()
                self.wasVideoEnabled = isVideoEnabled

                if isVideoEnabled == true {
                    self.suggestedVideoStateSubject.send(.disabled)
                }
            }
            .store(in: &cancellables)

        notificationCenter
            .publisher(for: UIApplication.didBecomeActiveNotification, object: nil)
            .sink { [weak self] _ in
                guard let self = self else {
                    return
                }

                if self.wasVideoEnabled == true {
                    self.suggestedVideoStateSubject.send(.enabled)
                }
            }
            .store(in: &cancellables)
    }
}

extension PhenixMediaInterruptionService {
    enum SuggestedVideoState {
        case enabled
        case disabled
    }
}
