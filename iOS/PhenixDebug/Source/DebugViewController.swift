//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import UIKit

/// A Phenix Debug view controller, that provides information about the currently used PhenixSdk version
/// and the current app version, as well as provide possibility to share the PhenixSdk logs.
public class DebugViewController: UIViewController {
    private lazy var textView: UITextView = {
        let view = UITextView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.isScrollEnabled = true
        view.isSelectable = false
        view.font = .preferredFont(forTextStyle: .body)
        return view
    }()

    private lazy var shareButton: UIButton = {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.setTitle("Share App logs", for: .normal)
        button.setTitleColor(.systemOrange, for: .normal)
        button.titleEdgeInsets = UIEdgeInsets(top: 7, left: 10, bottom: 0, right: 0)
        button.titleLabel?.font = .preferredFont(forTextStyle: .footnote)
        button.tintColor = .systemOrange
        if #available(iOS 13.0, *) {
            button.setImage(UIImage(systemName: "square.and.arrow.up"), for: .normal)
        }
        return button
    }()

    private lazy var closeButton: UIButton = {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.setTitle("Close", for: .normal)
        if #available(iOS 13.0, *) {
            button.tintColor = .label
        } else {
            button.tintColor = .black
        }
        return button
    }()

    private let viewModel: ViewModel
    private let queue: DispatchQueue = .main

    public init(viewModel: ViewModel) {
        self.viewModel = viewModel
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public override func viewDidLoad() {
        super.viewDidLoad()
        setupElements()
        textView.text = viewModel.informativeText
    }

    // MARK: - Private methods

    private func setupElements() {
        if #available(iOS 13.0, *) {
            view.backgroundColor = .systemBackground
        } else {
            view.backgroundColor = .white
        }

        textView.translatesAutoresizingMaskIntoConstraints = false

        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.addTarget(self, action: #selector(closeButtonTapped), for: .touchUpInside)

        shareButton.translatesAutoresizingMaskIntoConstraints = false
        shareButton.addTarget(self, action: #selector(shareButtonTapped), for: .touchUpInside)

        view.addSubview(textView)
        view.addSubview(closeButton)
        view.addSubview(shareButton)

        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.topAnchor,
                constant: closeButton.directionalLayoutMargins.top
            ),
            closeButton.leadingAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.leadingAnchor,
                constant: closeButton.directionalLayoutMargins.leading
            ),
            closeButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 44),
            closeButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),

            textView.topAnchor.constraint(
                equalTo: closeButton.bottomAnchor,
                constant: closeButton.directionalLayoutMargins.bottom
            ),
            textView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: textView.directionalLayoutMargins.leading),
            textView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: textView.directionalLayoutMargins.trailing),

            shareButton.topAnchor.constraint(
                equalTo: textView.bottomAnchor,
                constant: -shareButton.directionalLayoutMargins.top
            ),
            shareButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 150),
            shareButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            shareButton.bottomAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.bottomAnchor,
                constant: -shareButton.directionalLayoutMargins.bottom
            )
        ])
    }

    @objc
    private func shareButtonTapped() {
        viewModel.collectSDKLogs { [weak self] messages in
            self?.queue.async {
                guard let self = self else { return }
                let vc = UIActivityViewController(
                    activityItems: [messages ?? "No logs found"],
                    applicationActivities: nil
                )
                vc.popoverPresentationController?.sourceView = self.shareButton
                self.present(vc, animated: true)
            }
        }
    }

    @objc
    private func closeButtonTapped() {
        dismiss(animated: true)
    }
}
