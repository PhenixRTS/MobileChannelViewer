//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

public struct PhenixDeeplinkService<Model: PhenixDeeplinkModelProvider> {
    private let components: URLComponents

    public init?(url: URL) {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return nil
        }

        self.components = components
    }

    public func decode() -> Model? {
        Model(components: components)
    }
}
