//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

protocol Storyboarded {
    static func instantiate(from board: UIStoryboard) -> Self
}

extension Storyboarded where Self: UIViewController {
    static func instantiate(from board: UIStoryboard = .main) -> Self {
        let identifier = String(describing: self)
        // swiftlint:disable:next force_cast
        return board.instantiateViewController(withIdentifier: identifier) as! Self
    }
}

extension UIStoryboard {
    static let main = UIStoryboard(name: "Main", bundle: .main)
    static let launchScreenImitation = UIStoryboard(name: "LaunchScreenImitation", bundle: .main)
}
