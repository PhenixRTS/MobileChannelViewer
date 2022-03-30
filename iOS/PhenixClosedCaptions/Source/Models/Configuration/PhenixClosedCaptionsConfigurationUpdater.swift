//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation

extension PhenixClosedCaptionsConfiguration {
    struct Updater {
        let configuration: PhenixClosedCaptionsConfiguration
        let windowUpdate: PhenixWindowUpdate

        /// Use **PhenixWindowUpdate** to provide the new configuration values to the current configuration.
        ///
        /// Return tuple contains to parameters - configuration and needsConstraintUpdate.
        ///
        /// - *configuration*: Newly constructed configuration, which contains old-unchanged values and the new values from the **PhenixWindowUpdate**.
        /// - *needsConstraintUpdate*: A property that described that some of the configuration changes needs to be reflected with the constraint update.
        /// - Returns: Tuple with 2 parameters - configuration and needsConstraintUpdate.
        func update() -> (configuration: PhenixClosedCaptionsConfiguration, needsConstraintUpdate: Bool) {
            var configuration = configuration
            var needsConstraintUpdate = false

            if let backgroundAlpha = windowUpdate.backgroundAlpha {
                configuration.textBackgroundAlpha = backgroundAlpha
            }

            if let justify = windowUpdate.justify {
                configuration.justify = justify
            }

            if let backgroundColor = UIColor(hex: windowUpdate.backgroundColor) {
                configuration.textBackgroundColor = backgroundColor
            }

            if let wordWrap = windowUpdate.wordWrap {
                configuration.wordWrap = wordWrap
            }

            if let zOrder = windowUpdate.zOrder {
                configuration.zOrder = zOrder
            }

            if let visible = windowUpdate.visible {
                configuration.visible = visible
            }

            if let anchorPointOnTextWindow = windowUpdate.anchorPointOnTextWindow?.cgPoint {
                configuration.anchorPointOnTextWindow = anchorPointOnTextWindow
                needsConstraintUpdate = true
            }

            if let positionOfTextWindow = windowUpdate.positionOfTextWindow?.cgPoint {
                configuration.positionOfTextWindow = positionOfTextWindow
                needsConstraintUpdate = true
            }

            if let widthInCharacters = windowUpdate.widthInCharacters {
                configuration.widthInCharacters = widthInCharacters
                needsConstraintUpdate = true
            }

            if let heightInTextLines = windowUpdate.heightInTextLines {
                configuration.heightInTextLines = heightInTextLines
                needsConstraintUpdate = true
            }

            return (configuration: configuration, needsConstraintUpdate: needsConstraintUpdate)
        }
    }
}
