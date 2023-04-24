//
//  Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

public extension PhenixDeeplinkService {
    static func makeDeeplink(_ userActivity: NSUserActivity) -> Model? {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb else {
            return nil
        }

        guard let url = userActivity.webpageURL else {
            return nil
        }

        let service = PhenixDeeplinkService<Model>(url: url)
        let deeplink = service?.decode()
        return deeplink
    }
}
