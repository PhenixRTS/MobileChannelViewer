//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

public extension PhenixDeeplinkService {
    static func makeDeeplink(_ launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Model? {
        guard let options = launchOptions?[.userActivityDictionary] as? [AnyHashable: Any] else { return nil }
        guard let userActivity = options[UIApplication.LaunchOptionsKey.userActivityKey] as? NSUserActivity else { return nil }
        return makeDeeplink(userActivity)
    }

    static func makeDeeplink(_ userActivity: NSUserActivity) -> Model? {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb else { return nil }
        guard let url = userActivity.webpageURL else { return nil }

        let service = PhenixDeeplinkService<Model>(url: url)
        let deeplink = service?.decode()
        return deeplink
    }
}

private extension UIApplication.LaunchOptionsKey {
    static let userActivityKey = "UIApplicationLaunchOptionsUserActivityKey"
}

