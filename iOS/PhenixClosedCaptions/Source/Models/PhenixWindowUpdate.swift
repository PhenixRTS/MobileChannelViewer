//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import CoreGraphics.CGGeometry
import UIKit.UILabel

public struct PhenixWindowUpdate: Codable {
    public var anchorPointOnTextWindow: Point?
    public var positionOfTextWindow: Point?
    public var widthInCharacters: Int?
    public var heightInTextLines: Int?
    public var visible: Bool?
    public var backgroundColor: String?
    public var backgroundAlpha: CGFloat?
    public var wordWrap: Bool?
    public var justify: Justification?
    public var zOrder: Int?
}

extension PhenixWindowUpdate {
    public struct Point: Codable {
        public var x: Float
        public var y: Float

        public var cgPoint: CGPoint {
            CGPoint(x: CGFloat(x), y: CGFloat(y))
        }
    }

    public enum Justification: String, Codable {
        case left
        case center
        case right
        case full

        public var nsTextAlignment: NSTextAlignment {
            switch self {
            case .left:   return .left
            case .center: return .center
            case .right:  return .right
            case .full:   return .justified
            }
        }
    }
}

extension NSTextAlignment {
    public var justification: PhenixWindowUpdate.Justification {
        switch self {
        case .left:       return .left
        case .center:     return .center
        case .right:      return .right
        case .justified:  return .full
        case .natural:    return .left
        @unknown default: return .left
        }
    }
}
