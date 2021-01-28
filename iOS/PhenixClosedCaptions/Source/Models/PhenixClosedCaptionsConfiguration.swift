//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

public struct PhenixClosedCaptionsConfiguration: Equatable {
    /// Closed Caption window relative anchor point to top-left corner
    ///
    /// `(x: 0.5, y: 0.5)` - center of the view,
    ///
    /// `(x: 1.0, y: 1.0)` - bottom-right corner
    public var anchorPointOnTextWindow: CGPoint

    /// Closed Caption window relative position inside the Closed Caption window container view
    ///
    /// `(x: 0.0, y: 0.0)` - top-left corner,
    ///
    /// `(x: 0.5, y: 0.5)` - center of the view,
    ///
    /// `(x: 0.5, y: 1.0)` - bottom-center of the view
    public var positionOfTextWindow: CGPoint

    /// Closed Caption window character count in one line
    public var widthInCharacters: Int

    /// Closed Caption window line count
    public var heightInTextLines: Int

    /// Closed Caption window background color
    public var textBackgroundColor: UIColor

    /// Closed Caption window background alpha level (valid value range: 0.0...0.1)
    public var textBackgroundAlpha: CGFloat

    /// Closed Caption window visibility for newly added views
    public var visible: Bool

    /// Closed Caption window position in a stack of overlapping windows
    ///
    /// Higher value windows will overlap windows with smaller value
    public var zOrder: Int

    /// Closed Caption window text alignment
    public var justify: PhenixWindowUpdate.Justification

    /// Closed Caption window text wrapping if it reaches the end of the available space
    public var wordWrap: Bool

    public init(anchorPointOnTextWindow: CGPoint, positionOfTextWindow: CGPoint, widthInCharacters: Int, heightInTextLines: Int, textBackgroundColor: UIColor, textBackgroundAlpha: CGFloat, visible: Bool, zOrder: Int, justify: PhenixWindowUpdate.Justification, wordWrap: Bool) {
        self.anchorPointOnTextWindow = anchorPointOnTextWindow
        self.positionOfTextWindow = positionOfTextWindow
        self.widthInCharacters = widthInCharacters
        self.heightInTextLines = heightInTextLines
        self.textBackgroundColor = textBackgroundColor
        self.textBackgroundAlpha = textBackgroundAlpha
        self.visible = visible
        self.zOrder = zOrder
        self.justify = justify
        self.wordWrap = wordWrap
    }
}

extension PhenixClosedCaptionsConfiguration {
    public static let `default` = PhenixClosedCaptionsConfiguration(
        anchorPointOnTextWindow: CGPoint(x: 0.5, y: 1.0),
        positionOfTextWindow: CGPoint(x: 0.5, y: 0.9),
        widthInCharacters: 32,
        heightInTextLines: 1,
        textBackgroundColor: .black,
        textBackgroundAlpha: 1.0,
        visible: true,
        zOrder: 0,
        justify: .center,
        wordWrap: true
    )
}
