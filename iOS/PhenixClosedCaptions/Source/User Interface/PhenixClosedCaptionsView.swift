//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import UIKit

public class PhenixClosedCaptionsView: UIView {
    internal private(set) var windows: [UInt: PhenixWindowView] = [:]

    // MARK: - Public properties

    /// View property configuration
    public var configuration: PhenixClosedCaptionsConfiguration = .default {
        didSet {
            os_log(.debug, log: .containerView, "View configuration changed: %{PRIVATE}s", String(reflecting: configuration))
        }
    }

    internal override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    internal required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    public override func layoutSubviews() {
        windows.forEach { $0.value.setNeedsUpdateConstraints() }
        super.layoutSubviews()
    }

    internal func getWindowIndexList() -> Set<UInt> {
        Set(windows.map { $0.key })
    }

    internal func update(_ windowUpdate: PhenixWindowUpdate, forWindow index: UInt) {
        // Retrieve the view from the set
        let window = getWindow(withIndex: index)

        // TODO: Make those properties "sticky" in the cache.

        if let backgroundAlpha = windowUpdate.backgroundAlpha {
            window.configuration.textBackgroundAlpha = backgroundAlpha
        }

        if let justify = windowUpdate.justify {
            window.configuration.justify = justify
        }

        if let backgroundColor = UIColor(hex: windowUpdate.backgroundColor) {
            window.configuration.textBackgroundColor = backgroundColor
        }

        if let wordWrap = windowUpdate.wordWrap {
            window.configuration.wordWrap = wordWrap
        }

        if let zOrder = windowUpdate.zOrder {
            window.configuration.zOrder = zOrder
        }

        if let visible = windowUpdate.visible {
            window.isHidden = visible == false
        }

        if let anchorPointOnTextWindow = windowUpdate.anchorPointOnTextWindow?.cgPoint {
            window.configuration.anchorPointOnTextWindow = anchorPointOnTextWindow
            window.setNeedsUpdateConstraints()
        }

        if let positionOfTextWindow = windowUpdate.positionOfTextWindow?.cgPoint {
            window.configuration.positionOfTextWindow = positionOfTextWindow
            window.setNeedsUpdateConstraints()
        }

        if let widthInCharacters = windowUpdate.widthInCharacters {
            window.configuration.widthInCharacters = widthInCharacters
            window.setNeedsUpdateConstraints()
        }

        if let heightInTextLines = windowUpdate.heightInTextLines {
            window.configuration.heightInTextLines = heightInTextLines
            window.setNeedsUpdateConstraints()
        }
    }

    internal func update(_ texts: [PhenixTextUpdate], forWindow index: UInt) {
        // Retrieve the view from the set
        let window = getWindow(withIndex: index)

        // Combine captions from all `PhenixTextUpdate` objects together into one string
        let caption: String = texts
            .map { $0.caption }
            .reduce("", +)

        // Configure the text view.
        window.set(text: caption)

        // If the caption is empty, then we need to hide the view (but not remove the view)
        window.isHidden = caption.isEmpty
    }

    internal func remove(windowWithIndex index: UInt) {
        let window = windows.removeValue(forKey: index)
        window?.removeFromSuperview()
    }

    internal func removeAllWindows() {
        windows.forEach { $0.value.removeFromSuperview() }
        windows.removeAll()
    }

    internal func getWindow(withIndex index: UInt) -> PhenixWindowView {
        // Retrieve the view from the set or create a new one.
        let window = windows[index] ?? PhenixWindowView()

        if windows[index] == nil { // If the window didn't exist...
            window.translatesAutoresizingMaskIntoConstraints = false
            window.tag = Int(index)
            window.configuration = configuration

            // Add to the container.
            addSubview(window)

            // Insert into the view set.
            windows[index] = window
        }

        return window
    }
}

private extension PhenixClosedCaptionsView {
    func setup() {
        isOpaque = false
        backgroundColor = .clear
    }
}
