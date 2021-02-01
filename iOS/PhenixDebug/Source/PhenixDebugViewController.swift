//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk
import UIKit

public class PhenixDebugViewController: UIViewController {
    private var textView: UITextView!
    private var shareButton: UIButton!
    private var closeButton: UIButton!

    private let viewModel: PhenixDebugViewModel

    public init(roomExpress: PhenixRoomExpress) {
        self.viewModel = PhenixDebugViewModel(roomExpress: roomExpress)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public override func viewDidLoad() {
        super.viewDidLoad()

        if #available(iOS 13.0, *) {
            view.backgroundColor = .systemBackground
        } else {
            view.backgroundColor = .white
        }

        setup()
        let text = """
            About

            App version: \(UIApplication.appVersion ?? "N/A") build: \(UIApplication.appBuildVersion ?? "N/A")
            SDK version: \(viewModel.frameworkInformation.version ?? "N/A") build: \(viewModel.frameworkInformation.buildVersion ?? "N/A")
        """
        textView.text = text
    }
}

private extension PhenixDebugViewController {
    func setup() {
        textView = UITextView.makeTextView()

        closeButton = UIButton.makeCloseButton()
        closeButton.addTarget(self, action: #selector(closeButtonTapped), for: .touchUpInside)

        shareButton = UIButton.makeShareButton()
        shareButton.addTarget(self, action: #selector(shareButtonTapped), for: .touchUpInside)

        view.addSubview(textView)
        view.addSubview(closeButton)
        view.addSubview(shareButton)

        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: closeButton.directionalLayoutMargins.top),
            closeButton.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: closeButton.directionalLayoutMargins.leading),
            closeButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 44),
            closeButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),

            textView.topAnchor.constraint(equalTo: closeButton.bottomAnchor, constant: closeButton.directionalLayoutMargins.bottom),
            textView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            textView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),

            shareButton.topAnchor.constraint(equalTo: textView.bottomAnchor, constant: -shareButton.directionalLayoutMargins.top),
            shareButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 150),
            shareButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            shareButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -shareButton.directionalLayoutMargins.bottom)
        ])
    }

    @objc
    func shareButtonTapped() {
        viewModel.collectPCastLogs { messages in
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                let vc = UIActivityViewController(activityItems: [messages ?? "No logs found"], applicationActivities: nil)
                vc.popoverPresentationController?.sourceView = self.shareButton
                self.present(vc, animated: true)
            }
        }
    }

    @objc
    func closeButtonTapped() {
        dismiss(animated: true)
    }
}

fileprivate extension UITextView {
    static func makeTextView() -> UITextView {
        let view = UITextView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.isScrollEnabled = true
        view.isSelectable = false
        view.font = .preferredFont(forTextStyle: .body)
        return view
    }
}

fileprivate extension UIButton {
    static func makeShareButton() -> UIButton {
        let button = UIButton(type: .system)

        button.translatesAutoresizingMaskIntoConstraints = false
        if #available(iOS 13.0, *) {
            button.setImage(UIImage(systemName: "square.and.arrow.up"), for: .normal)
        }
        button.setTitle("Share App logs", for: .normal)
        button.setTitleColor(.systemOrange, for: .normal)
        button.titleEdgeInsets = UIEdgeInsets(top: 7, left: 10, bottom: 0, right: 0)
        button.titleLabel?.font = .preferredFont(forTextStyle: .footnote)
        button.tintColor = .systemOrange

        return button
    }

    static func makeCloseButton() -> UIButton {
        let button = UIButton(type: .system)

        button.translatesAutoresizingMaskIntoConstraints = false
        if #available(iOS 13.0, *) {
            button.tintColor = .label
        } else {
            button.tintColor = .black
        }
        button.setTitle("Close", for: .normal)

        return button
    }
}
