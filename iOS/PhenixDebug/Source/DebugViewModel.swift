//
//  Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

extension DebugViewController {
    /// A view model, which contains all the necessary instances to be able to provide the information for the view controller.
    public class ViewModel {
        private let pcast: PhenixPCast
        private let inspector: SDKInspector

        lazy var informativeText: String = {
            let bundle = Bundle.main
            let text = """
            About

            App version: \(bundle.appVersion ?? "N/A") build: \(bundle.appBuildVersion ?? "N/A")
            SDK version: \(inspector.version ?? "N/A") build: \(inspector.buildVersion ?? "N/A")
            """
            return text
        }()

        public init(pcast: PhenixPCast) {
            self.pcast = pcast
            self.inspector = .init()
        }

        public func collectSDKLogs(completion: @escaping (String?) -> Void) {
            pcast.collectLogMessages { _, status, messages in
                guard let messages = messages, status == .ok else {
                    completion(nil)
                    return
                }

                completion(messages)
            }
        }
    }
}
