//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixDeeplink

class AppSession {
    enum ConfigurationError: Error {
        case missingMandatoryDeeplinkProperties
        case mismatch
    }

    private(set) var alias: String
    private(set) var authToken: String
    private(set) var streamToken: String

    init(deeplink: PhenixDeeplinkModel) throws {
        guard let authToken = deeplink.authToken,
              let streamToken = deeplink.streamToken,
              let alias = deeplink.selectedAlias else {
                  throw ConfigurationError.missingMandatoryDeeplinkProperties
              }

        self.authToken = authToken
        self.streamToken = streamToken
        self.alias = alias
    }

    func validate(_ deeplink: PhenixDeeplinkModel) throws {
        if let token = deeplink.authToken, token != self.authToken {
            throw ConfigurationError.mismatch
        }

        if let token = deeplink.streamToken, token != self.streamToken {
            throw ConfigurationError.mismatch
        }

        if let token = deeplink.selectedAlias, token != self.alias {
            throw ConfigurationError.mismatch
        }
    }
}

extension AppSession: Equatable {
    static func == (lhs: AppSession, rhs: AppSession) -> Bool {
        lhs.authToken == rhs.authToken
        && lhs.streamToken == rhs.streamToken
        && lhs.alias == rhs.alias
    }
}
