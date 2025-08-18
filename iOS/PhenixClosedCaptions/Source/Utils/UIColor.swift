//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

extension UIColor {
    public convenience init?(hex: String?) {
        guard let hex = hex else {
            return nil
        }

        let r, g, b, a: CGFloat

        guard hex.hasPrefix("#") else {
            return nil
        }

        let start = hex.index(hex.startIndex, offsetBy: 1)
        let hexColor = String(hex[start...])
        let scanner = Scanner(string: hexColor)

        guard hexColor.count == 6 || hexColor.count == 8 else {
            return nil
        }

        var hexNumber: UInt64 = 0

        guard scanner.scanHexInt64(&hexNumber) else {
            return nil
        }

        r = CGFloat((hexNumber & 0xff000000) >> 24) / 255
        g = CGFloat((hexNumber & 0x00ff0000) >> 16) / 255
        b = CGFloat((hexNumber & 0x0000ff00) >> 8) / 255
        a = hexColor.count == 8 ? (CGFloat(hexNumber & 0x000000ff) / 255) : 1.0

        self.init(red: r, green: g, blue: b, alpha: a)
    }
}
