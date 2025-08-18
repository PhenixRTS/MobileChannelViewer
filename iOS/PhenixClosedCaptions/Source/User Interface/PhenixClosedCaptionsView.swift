//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
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

        let updater = PhenixClosedCaptionsConfiguration.Updater(
            configuration: window.configuration,
            windowUpdate: windowUpdate
        )
        let (configuration, needsConstraintUpdate) = updater.update()
        window.configuration = configuration

        if needsConstraintUpdate {
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

    /// A way to retrieve the window from the current window collection.
    ///
    /// If the window does not exist in the window collection, then it will be created and also saved in this collection.
    /// - Parameter index: Window tag index
    /// - Returns: Retrieved window view
    internal func getWindow(withIndex index: UInt) -> PhenixWindowView {
        if let window = windows[index] {
            return window
        }

        // If the window don't exist...
        let window = createWindow(withIndex: index)
        setupWindow(window)

        return window
    }

    /// Create new window view
    /// - Parameter index: Window tag index
    /// - Returns: Created window view
    internal func createWindow(withIndex index: UInt) -> PhenixWindowView {
        let window = PhenixWindowView()
        window.translatesAutoresizingMaskIntoConstraints = false
        window.tag = Int(index)
        window.configuration = configuration
        return window
    }

    /// Add the provided window to the view and save it in the windows list.
    /// - Parameter window: Provided window view
    internal func setupWindow(_ window: PhenixWindowView) {
        // Add to the container.
        addSubview(window)

        // Insert into the view set.
        windows[UInt(window.tag)] = window
    }
}

private extension PhenixClosedCaptionsView {
    func setup() {
        isOpaque = false
        backgroundColor = .clear
    }
}
