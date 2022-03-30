//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

public enum PhenixDeeplink {
    public static func makeDeeplink<Model: PhenixDeeplinkUrlModelRepresentable>(_ userActivity: NSUserActivity) -> Model? {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb else {
            return nil
        }

        guard let url = userActivity.webpageURL else {
            return nil
        }

        return makeDeeplink(url: url)
    }

    /// Creates a PhenixDeeplink model out of the Environment Variable if possible.
    ///
    /// Environment variable key `PHENIX_DEEPLINK_URL` represents the same deep link url, which could be used manually to open the app.
    /// This method looks for this environment variable and processes it, if it exists.
    /// - Returns: Model instance which conforms to the PhenixDeeplinkUrlModelRepresentable protocol.
    public static func makeDeeplinkFromEnvironment<Model: PhenixDeeplinkUrlModelRepresentable>() -> Model? {
        let key = "PHENIX_DEEPLINK_URL"
        let environment = ProcessInfo.processInfo.environment

        guard let value = environment[key] else {
            return nil
        }

        guard let url = URL(string: value) else {
            return nil
        }

        return makeDeeplink(url: url)
    }

    /// Creates a PhenixDeeplink model out of the provided URL if possible.
    /// - Returns: Model instance which conforms to the PhenixDeeplinkUrlModelRepresentable protocol.
    public static func makeDeeplink<Model: PhenixDeeplinkUrlModelRepresentable>(url: URL) -> Model? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return nil
        }

        return Model(components: components)
    }
}
