//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import PhenixDeeplink
import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    private static let logger = OSLog(identifier: "AppDelegate")

    private var bootstrap: Bootstrap!

    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Setup main window
        let window = UIWindow(frame: UIScreen.main.bounds)
        self.window = window

        // Setup launcher to initiate the necessary application components
        bootstrap = Bootstrap(window: window)

        #if DEBUG
        // Setup deeplink
        if let model: PhenixDeeplinkModel = PhenixDeeplink.makeDeeplinkFromEnvironment() {
            // To not freeze the user interface while the core instances
            // are prepared, move the code to a background queue execution.
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.process(deeplink: model)
            }
        }
        #endif

        return true
    }

    func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {
        guard let model: PhenixDeeplinkModel = PhenixDeeplink.makeDeeplink(userActivity) else {
            return false
        }

        process(deeplink: model)

        return true
    }

    /// Process provided deep link model.
    ///
    /// If necessary, the app will initialize the app's core from the deep link model values
    /// or validate the currently active session against the newly received deep link values.
    /// - Parameter model: Obtained deep link model
    private func process(deeplink model: PhenixDeeplinkModel) {
        let result = bootstrap.setup(with: model)

        switch result {
        case .success(.setupCompleted):
            break

        case .success(.setupCompletedPreviously):
            do {
                // If the application has already been set up,
                // we need to compare and check for any changes
                // in the currently received deep link model and
                // the current session.
                // If there are changes, we need to let the user
                // know that the app must be restarted through
                // the deep link again.
                try bootstrap.validateSession(deeplink: model)
            } catch {
                DispatchQueue.main.async {
                    Self.terminate(
                        afterDisplayingAlertWithTitle: "Configuration has changed.",
                        message: "Please restart the app through the deep link to apply the changes."
                    )
                }
            }

        case .failure(let error):
            os_log(.debug, log: Self.logger, "Error occurred: %{private}s", error.localizedDescription)

            DispatchQueue.main.async {
                Self.terminate(
                    afterDisplayingAlertWithTitle: "Failed to start the app.",
                    message: "Sorry, unrecoverable error occurred, please restart the app."
                )
            }
        }
    }
}

extension AppDelegate {
    /// Provide an alert with information and then terminate the application
    ///
    /// - Parameters:
    ///   - title: Title for the alert
    ///   - message: Message for the alert
    ///   - file: The file name to print with `message`. The default is the file
    ///   where `terminate(afterDisplayingAlertWithTitle:message:file:line:)` is called.
    ///   - line: The line number to print along with `message`. The default is the line number where
    ///   `terminate(afterDisplayingAlertWithTitle:message:file:line:)` is called.
    static func terminate(
        afterDisplayingAlertWithTitle title: String,
        message: String,
        file: StaticString = #file,
        line: UInt = #line
    ) {
        guard let delegate = UIApplication.shared.delegate as? AppDelegate,
              let window = delegate.window else {
            fatalError(message)
        }

        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Close app", style: .default) { _ in
            fatalError(message, file: file, line: line)
        })

        window.rootViewController?.presentedViewController?.dismiss(animated: false)
        window.rootViewController?.present(alert, animated: true)
    }

    static func present(alertWithTitle title: String, message: String? = nil) {
        guard let delegate = UIApplication.shared.delegate as? AppDelegate,
              let window = delegate.window else {
            fatalError("Could not load window instance.")
        }

        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))

        if let viewController = window.rootViewController?.presentedViewController {
            viewController.present(alert, animated: true)
        } else {
            window.rootViewController?.present(alert, animated: true)
        }
    }
}
