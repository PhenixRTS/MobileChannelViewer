//
//  Copyright 2024 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixDeeplink

public struct PhenixDeeplinkModel: PhenixDeeplinkModelProvider {
    var edgeToken: String?

    public init?(components: URLComponents) {
        if let string = components.queryItems?.first(where: { $0.name == "token" })?.value {
            self.edgeToken = string
        }
    }
}
