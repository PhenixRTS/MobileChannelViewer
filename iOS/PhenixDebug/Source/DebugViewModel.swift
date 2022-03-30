//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixCore

extension DebugViewController {
    /// A view model, which contains all the necessary instances to be able to provide the information for the view controller.
    public class ViewModel {
        private let core: PhenixCoreDebuggable
        private let inspector: PhenixDebug.SDKInspector

        lazy var informativeText: String = {
            let bundle = Bundle.main
            let text = """
            About

            App version: \(bundle.appVersion ?? "N/A") build: \(bundle.appBuildVersion ?? "N/A")
            SDK version: \(inspector.version ?? "N/A") build: \(inspector.buildVersion ?? "N/A")
            """
            return text
        }()

        public init(core: PhenixCoreDebuggable) {
            self.core = core
            self.inspector = .init()
        }

        public func collectSDKLogs(completion: @escaping (String?) -> Void) {
            core.collectLogs { logs in
                completion(logs)
            }
        }
    }
}
