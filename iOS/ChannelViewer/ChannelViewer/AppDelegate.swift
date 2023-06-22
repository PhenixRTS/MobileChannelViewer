//
//  Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import os.log
import PhenixDeeplink
import PhenixSdk
import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    private static let logger = OSLog(identifier: "AppDelegate")
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

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .moviePlayback)
        } catch {
            Self.terminate(
                afterDisplayingAlertWithTitle: "Setting up AVAudioSession failed: \(error)",
                message: "Please try to restart the application."
            )
        }

        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        if let viewController = self.window?.rootViewController as? ViewController {
            viewController.limitBandwidth()
        }
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        if let viewController = self.window?.rootViewController as? ViewController {
            viewController.disposeBandwidthLimit()
        }
    }

    // MARK: - Deeplink handling

    func application(
            _ application: UIApplication,
            continue userActivity: NSUserActivity,
            restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
        ) -> Bool {
        guard let deeplink = PhenixDeeplinkService<PhenixDeeplinkModel>.makeDeeplink(userActivity) else {
            return false
        }

        if let edgeToken = deeplink.edgeToken {
            PhenixConfiguration.edgeToken = edgeToken
        }

        if let delegate = UIApplication.shared.delegate as? AppDelegate,
           let window = delegate.window,
           let viewController = window.rootViewController as? ViewController {

            viewController.openDeepLink()
        }

        return true
    }
}
