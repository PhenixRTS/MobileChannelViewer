//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import os.log
import PhenixClosedCaptions
import PhenixCore
import PhenixDebug
import PhenixDeeplink
import UIKit

final class Bootstrap {
    enum AppState: Equatable {
        case notInitialized
        case initialized(AppSession, PhenixCore)
    }

    enum AppSetupState {
        case setupCompleted
        case setupCompletedPreviously
    }

    private let queue: DispatchQueue
    private let window: UIWindow?
    private let missingDeeplinkTimeInterval: TimeInterval = 5

    private let navigationController: UINavigationController

    /// Application debug view controller, which provides an option to retrieve the PhenixCore logs.
    private var debugViewController: DebugViewController?

    private var coreCancellable: AnyCancellable?
    private var missingDeeplinkWorkItem: DispatchWorkItem?

    private var state: AppState = .notInitialized

    init(window: UIWindow) {
        self.queue = DispatchQueue(label: "Bootstrap.Queue", attributes: .concurrent)
        self.window = window

        // Create launch view controller, which will hide all async loading
        let viewController = LaunchViewController.instantiate(from: .launchScreenImitation)

        // Create navigation controller
        navigationController = UINavigationController(rootViewController: viewController)
        navigationController.isNavigationBarHidden = true
        navigationController.navigationBar.isTranslucent = false

        // Display the navigation controller holding screen
        // which looks the same as the launch screen.
        window.rootViewController = navigationController
        window.makeKeyAndVisible()

        setupMissingDeeplinkWatchdog()
    }

    /// Initiates all necessary application instance creation.
    /// - Parameter model: Provided deep link model. This application does not work without a deep link.
    /// - Returns: State of the setup.
    func setup(with model: PhenixDeeplinkModel) -> Result<AppSetupState, Error> {
        queue.sync(flags: [.barrier]) {
            missingDeeplinkWorkItem?.cancel()
            missingDeeplinkWorkItem = nil

            guard state == .notInitialized else {
                return .success(.setupCompletedPreviously)
            }

            do {
                let session = try AppSession(deeplink: model)
                let core = setupCore(session: session)
                let closedCaptions = makeClosedCaptions(core: core)
                setupDebugView(core: core)

                state = .initialized(session, core)

                DispatchQueue.main.async {
                    let viewController = self.makeViewerViewController(
                        core: core,
                        closedCaptions: closedCaptions,
                        session: session
                    )

                    self.navigationController.setViewControllers([viewController], animated: true)
                }

                return .success(.setupCompleted)
            } catch {
                return .failure(error)
            }
        }
    }

    /// Validates current session properties against received deep link model properties.
    /// - Parameter model: Deep link model.
    func validateSession(deeplink model: PhenixDeeplinkModel) throws {
        guard case let AppState.initialized(session, _) = state else {
            return
        }

        try session.validate(model)
    }

    // MARK: - Private methods

    private func setupCore(session: AppSession) -> PhenixCore {
        let configuration = PhenixCore.Configuration(authToken: session.authToken)
        let core = PhenixCore(configuration: configuration, publisherQueue: .main)

        coreCancellable = core.eventPublisher
            .sink { completion in
                switch completion {
                case .failure(.unrecoverableError(let description)):
                    AppDelegate.terminate(
                        afterDisplayingAlertWithTitle: "Something went wrong!",
                        message:
                            """
                            Application entered in unrecoverable state \
                            and will be terminated (\(description ?? "N/A")).
                            """
                    )

                default:
                    AppDelegate.present(
                        alertWithTitle: "Something went wrong",
                        message: "Please restart the application."
                    )
                }
            } receiveValue: { _ in
                // No need to process any events here.
            }

        core.setup()

        return core
    }

    private func makeClosedCaptions(core: PhenixCore) -> PhenixClosedCaptionsController {
        PhenixClosedCaptionsController(core: core, queue: .main)
    }

    private func setupMissingDeeplinkWatchdog() {
        let workItem = DispatchWorkItem {
            AppDelegate.terminate(
                afterDisplayingAlertWithTitle: "No deep link provided",
                message: "Please start the application with appropriate deep link."
            )
        }

        missingDeeplinkWorkItem?.cancel()
        missingDeeplinkWorkItem = workItem

        DispatchQueue.main.asyncAfter(deadline: .now() + missingDeeplinkTimeInterval, execute: workItem)
    }

    @objc
    private func showDebugMenu() {
        if let viewController = debugViewController {
            window?.rootViewController?.latestViewController?.present(viewController, animated: true)
        }
    }

    private func setupDebugView(core: PhenixCore) {
        // It is possible to execute this command from a background queue,
        // therefore we need to transfer it to main queue execution,
        // because it involves user interface element creation, which
        // can happen only on the main queue.
        DispatchQueue.main.async {
            // Create the debug view
            let viewModel = DebugViewController.ViewModel(core: core)
            self.debugViewController = DebugViewController(viewModel: viewModel)

            // Create the debug gesture
            let tapGesture = UITapGestureRecognizer(target: self, action: #selector(self.showDebugMenu))
            tapGesture.numberOfTapsRequired = 5
            self.window?.addGestureRecognizer(tapGesture)
        }
    }

    private func makeViewerViewController(
        core: PhenixCore,
        closedCaptions: PhenixClosedCaptionsController,
        session: AppSession
    ) -> ViewerViewController {
        let viewModel = ViewerViewController.ViewModel(
            core: core,
            closedCaptions: closedCaptions,
            session: session
        )
        let viewController = ViewerViewController.instantiate()
        viewController.viewModel = viewModel

        return viewController
    }
}
