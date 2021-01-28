//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk
import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    public static let channelExpress: PhenixChannelExpress = { PhenixConfiguration.makeChannelExpress() }()

    var window: UIWindow?

    /// Provide an alert with information and then terminate the application
    ///
    /// - Parameters:
    ///   - title: Title for the alert
    ///   - message: Message for the alert
    ///   - file: The file name to print with `message`. The default is the file
    ///   where `terminate(afterDisplayingAlertWithTitle:message:file:line:)` is called.
    ///   - line: The line number to print along with `message`. The default is the line number where
    ///   `terminate(afterDisplayingAlertWithTitle:message:file:line:)` is called.
    static func terminate(afterDisplayingAlertWithTitle title: String, message: String, file: StaticString = #file, line: UInt = #line) {
        guard let delegate = UIApplication.shared.delegate as? AppDelegate,
              let window = delegate.window else {
            fatalError(message)
        }

        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Close app", style: .default) { _ in
            fatalError(message, file: file, line: line)
        })

        window.rootViewController?.present(alert, animated: true)
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        // Setup deeplink
        if let deeplink = PhenixConfiguration.makeDeeplink(launchOptions) {
            if let edgeToken = deeplink.edgeToken {
                PhenixConfiguration.edgeToken = edgeToken
                // Clear the default backend uri.
                PhenixConfiguration.backendUri = nil
            }

            if let backend = deeplink.backend {
                PhenixConfiguration.backendUri = backend
            }

            if let pcastUri = deeplink.uri {
                PhenixConfiguration.pcastUri = pcastUri
            }

            if let alias = deeplink.alias {
                channelAlias = alias
            }
        }

        return true
    }

    // MARK: - Deeplink handling

    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
        guard let deeplink = PhenixConfiguration.makeDeeplink(userActivity) else {
            return false
        }

        let terminate: () -> Void = {
            Self.terminate(
                afterDisplayingAlertWithTitle: "Configuration has changed.",
                message: "Please start the app again to apply the changes."
            )
        }

        if let edgeToken = deeplink.edgeToken, edgeToken != PhenixConfiguration.edgeToken {
            terminate()
            return false
        }

        if let backend = deeplink.backend, backend != PhenixConfiguration.backendUri {
            terminate()
            return false
        }

        if let uri = deeplink.uri, uri != PhenixConfiguration.pcastUri {
            terminate()
            return false
        }

        if let alias = deeplink.alias, alias != channelAlias {
            terminate()
            return false
        }

        return false
    }
}

// MARK: - Private helper properties and methods
private extension AppDelegate {
    var channelAlias: String? {
        get { viewController.channelAlias }
        set { viewController.channelAlias = newValue }
    }

    var viewController: ViewController {
        guard let vc = window?.rootViewController as? ViewController else {
            fatalError("ViewController could not be found.")
        }
        return vc
    }
}
