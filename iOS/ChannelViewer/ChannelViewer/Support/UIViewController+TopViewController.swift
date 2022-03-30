//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

extension UIViewController {
    /// Retrieves the top view controller of the view controller hierarchy.
    var latestViewController: UIViewController? {
        if let controller = self as? UINavigationController {
            return controller.topViewController?.latestViewController
        }
        if let controller = self as? UISplitViewController {
            return controller.viewControllers.last?.latestViewController
        }
        if let controller = self as? UITabBarController {
            return controller.selectedViewController?.latestViewController
        }
        if let controller = presentedViewController {
            return controller.latestViewController
        }
        return self
    }
}
