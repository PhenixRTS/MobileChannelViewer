//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

class PhenixWindowView: UIView {
    private var horizontalPositionConstraint: NSLayoutConstraint?
    private var verticalPositionConstraint: NSLayoutConstraint?
    private var widthConstraint: NSLayoutConstraint!
    private var heightConstraint: NSLayoutConstraint!

    internal var textView: PhenixTextView!

    internal var configuration: PhenixClosedCaptionsConfiguration = .default {
        didSet {
            setVisualization(for: textView)
            setVisualizationForWindow()
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

    override func didMoveToSuperview() {
        super.didMoveToSuperview()
        setSuperviewPositionConstraints()
    }

    override func updateConstraints() {
        updateFrameConstraints()
        updateSuperviewPositionConstraints()
        super.updateConstraints()
    }

    internal func set(text: String) {
        guard text.isEmpty == false else {
            return
        }

        textView.caption = text

        // Update view layout
        layoutIfNeeded()
    }
}

private extension PhenixWindowView {
    func setup() {
        textView = PhenixTextView()
        setVisualization(for: textView)

        addSubview(textView)

        NSLayoutConstraint.activate([
            textView.topAnchor.constraint(equalTo: topAnchor),
            textView.leadingAnchor.constraint(equalTo: leadingAnchor),
            textView.trailingAnchor.constraint(equalTo: trailingAnchor),
            textView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        setFrameConstraints()
    }

    func setFrameConstraints() {
        widthConstraint = widthAnchor.constraint(equalToConstant: 0)
        heightConstraint = heightAnchor.constraint(equalToConstant: 0)

        updateFrameConstraints()

        NSLayoutConstraint.activate([widthConstraint, heightConstraint])

        heightConstraint.isActive = configuration.wordWrap == false
    }

    func updateFrameConstraints() {
        let size = calculateFrameSize(forCharacterCountInLine: configuration.widthInCharacters, lineCount: configuration.heightInTextLines, font: PhenixTextView.font)

        widthConstraint.constant = size.width
        // Add additional heightInTextLines count because
        // UILabels and other text representation views
        // add small amount of inset inside the text view.
        heightConstraint.constant = size.height + CGFloat(configuration.heightInTextLines)
    }

    func setSuperviewPositionConstraints() {
        guard let superview = superview else {
            return
        }

        // Remove previously set constraints
        NSLayoutConstraint.deactivate([horizontalPositionConstraint, verticalPositionConstraint].compactMap { $0 })

        // Add position constraints
        let horizontal = centerXAnchor.constraint(equalTo: superview.centerXAnchor)
        horizontal.priority = .defaultHigh // Border constrains must be priority.

        let vertical = centerYAnchor.constraint(equalTo: superview.centerYAnchor)
        vertical.priority = .defaultHigh // Border constrains must be priority.

        // Set constraints
        horizontalPositionConstraint = horizontal
        verticalPositionConstraint = vertical

        // Calculate specific anchor point to which position the view in superview
        updateSuperviewPositionConstraints()

        NSLayoutConstraint.activate([horizontal, vertical])
    }

    func updateSuperviewPositionConstraints() {
        guard let superview = superview else {
            return
        }

        assert(0.0...1.0 ~= configuration.anchorPointOnTextWindow.x)
        assert(0.0...1.0 ~= configuration.anchorPointOnTextWindow.y)
        assert(0.0...1.0 ~= configuration.positionOfTextWindow.x)
        assert(0.0...1.0 ~= configuration.positionOfTextWindow.y)

        // Calculate modified position of the window from its superview center position
        let calculatedXPosition = (0.5 - configuration.positionOfTextWindow.x) * -1.0 * superview.bounds.width
        let calculatedYPosition = (0.5 - configuration.positionOfTextWindow.y) * -1.0 * superview.bounds.height

        // Calculate modified anchor point inside this view against which it will be positioned
        let calculatedXAnchorPoint = (0.5 - configuration.anchorPointOnTextWindow.x) * frame.width
        let calculatedYAnchorPoint = (0.5 - configuration.anchorPointOnTextWindow.y) * frame.height

        horizontalPositionConstraint?.constant = calculatedXPosition + calculatedXAnchorPoint
        verticalPositionConstraint?.constant = calculatedYPosition + calculatedYAnchorPoint
    }

    func setVisualization(for textView: PhenixTextView) {
        textView.backgroundAlpha = configuration.textBackgroundAlpha
        textView.justify = configuration.justify
        textView.backgroundColor = configuration.textBackgroundColor
        textView.wordWrap = configuration.wordWrap
        textView.zOrder = configuration.zOrder

        textView.reloadCaptionStyle()
    }

    func setVisualizationForWindow() {
        isHidden = configuration.visible == false
    }
}

fileprivate extension UIView {
    /// Calculate frame size by providing character count for one line and total line count
    /// - Parameters:
    ///   - characterCount: Count for characters in one line
    ///   - lineCount: Count for lines
    ///   - font: Provided font for the potential string
    /// - Returns: Size of the frame which can contain the string in provided character and line count
    func calculateFrameSize(forCharacterCountInLine characterCount: Int, lineCount: Int, font: UIFont) -> CGSize {
        let attributes: [NSAttributedString.Key : Any] = [.font: font]
        let string = String(repeating: "W", count: characterCount) as NSString
        let containerRect = CGSize(width: .max, height: .max)
        let size = string.boundingRect(
            with: containerRect,
            options: [.usesLineFragmentOrigin],
            attributes: attributes,
            context: nil
        ).size
        return CGSize(width: ceil(size.width), height: ceil(size.height) * CGFloat(lineCount))
    }
}
