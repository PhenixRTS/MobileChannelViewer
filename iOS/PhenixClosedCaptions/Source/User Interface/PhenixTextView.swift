//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

/// Closed Caption Text view
///
/// Closed Caption view contains a black background with a white caption text on it by default.
/// Caption text supports Dynamic Type for automatic size changes.
internal final class PhenixTextView: UIView {
    internal static var font: UIFont = {
        if #available(iOS 13.0, *) {
            return UIFont.monospacedSystemFont(ofSize: 14, weight: .regular)
        } else {
            return .preferredFont(forTextStyle: .footnote)
        }
    }()

    private var captionLabel = UILabel.makeCaption()
    private var _backgroundColor: UIColor = .clear

    internal var captionAttributes: [NSAttributedString.Key: Any] {
        let style = NSMutableParagraphStyle()
        style.alignment = justify.nsTextAlignment
        style.lineBreakMode = wordWrap ? .byWordWrapping : .byTruncatingTail

        return [
            .font: Self.font,
            .foregroundColor: textColor,
            .backgroundColor: _backgroundColor,
            .paragraphStyle: style
        ]
    }

    /// Displayed caption text
    internal var caption: String {
        get { captionLabel.attributedText?.string ?? "" }
        set { setAttributedText(newValue) }
    }

    internal override var backgroundColor: UIColor? {
        get { _backgroundColor }
        set { _backgroundColor = (newValue ?? .clear).withAlphaComponent(backgroundAlpha) }
    }

    internal var backgroundAlpha: CGFloat = 1.0 {
        didSet { _backgroundColor = _backgroundColor.withAlphaComponent(backgroundAlpha) }
    }

    internal var justify: PhenixWindowUpdate.Justification = .center
    internal var textColor: UIColor = .white
    internal var wordWrap: Bool = true
    internal var zOrder: Int = 0 {
        didSet { layer.zPosition = CGFloat(zOrder) }
    }

    internal override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    internal required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    internal func reloadCaptionStyle() {
        setAttributedText(caption)
    }
}

private extension PhenixTextView {
    func setup() {
        isOpaque = false

        translatesAutoresizingMaskIntoConstraints = false
        addSubview(captionLabel)

        setInnerElementConstraints()
    }

    func setInnerElementConstraints() {
        let constraints: [NSLayoutConstraint] = [
            captionLabel.topAnchor.constraint(equalTo: topAnchor),
            captionLabel.leadingAnchor.constraint(equalTo: leadingAnchor),
            captionLabel.trailingAnchor.constraint(equalTo: trailingAnchor),
            captionLabel.bottomAnchor.constraint(equalTo: bottomAnchor)
        ]

        NSLayoutConstraint.activate(constraints)
    }

    func setAttributedText(_ text: String) {
        if #available(iOS 13.0, *) {
            captionLabel.attributedText = NSAttributedString(string: text, attributes: captionAttributes)
        } else {
            let attributedText = NSMutableAttributedString(string: text)

            // iOS 12 is displaying empty lines with a background color.
            // Empty lines must have clear background color.
            text.enumerateSubstrings(in: text.startIndex..<text.endIndex, options: .byLines) {
                substring, substringRange, _, _ in
                // Check if the line is not empty and only then provide formatting for it.
                if substring?.isEmpty == false {
                    attributedText.addAttributes(self.captionAttributes, range: NSRange(substringRange, in: text))
                }
            }

            captionLabel.attributedText = attributedText
        }
    }
}

fileprivate extension UILabel {
    static func makeCaption() -> UILabel {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.numberOfLines = 0
        label.font = PhenixTextView.font
        label.adjustsFontForContentSizeCategory = true
        label.textColor = .white
        return label
    }
}
