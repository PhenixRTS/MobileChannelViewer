//
//  Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixDeeplink

public struct PhenixDeeplinkModel: PhenixDeeplinkModelProvider {
    var alias: String?
    var uri: URL?
    var backend: URL?
    var edgeToken: String?

    public init?(components: URLComponents) {
        self.alias = components.fragment

        if let string = components.queryItems?.first(where: { $0.name == "uri" })?.value {
            self.uri = URL(string: string)
        }

        if let string = components.queryItems?.first(where: { $0.name == "backend" })?.value {
            self.backend = URL(string: string)
        }

        if let string = components.queryItems?.first(where: { $0.name == "edgeToken" })?.value {
            self.edgeToken = string
        }
    }
}
