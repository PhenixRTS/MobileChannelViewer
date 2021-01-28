//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

@testable import PhenixClosedCaptions
import XCTest

class PhenixClosedCaptionsViewIntegrationTests: XCTestCase {
    func testWindowUpdateOverridesDefaultViewConfiguration() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let configuration = PhenixClosedCaptionsConfiguration.default
        let textUpdate = PhenixTextUpdate(timestamp: 123456789, caption: "Test Caption")
        let windowUpdate = PhenixWindowUpdate(anchorPointOnTextWindow: .init(x: 0.25, y: 0.25))
        XCTAssertNotEqual(configuration.anchorPointOnTextWindow, windowUpdate.anchorPointOnTextWindow?.cgPoint)

        // When
        sut.configuration = configuration // Provide custom configuration
        sut.update([textUpdate], forWindow: 0) // Create text view

        let window = sut.getWindow(withIndex: 0)
        XCTAssertEqual(window.configuration.anchorPointOnTextWindow, sut.configuration.anchorPointOnTextWindow)

        sut.update(windowUpdate, forWindow: 0) // Update text window

        XCTAssertEqual(window.configuration.anchorPointOnTextWindow, windowUpdate.anchorPointOnTextWindow?.cgPoint)
    }

    func testViewUsesLastWindowUpdateConfigurationNotDefaultConfiguration() {
        // Given
        let sut = PhenixClosedCaptionsView()
        var modifiedConfiguration = PhenixClosedCaptionsConfiguration.default
        modifiedConfiguration.anchorPointOnTextWindow = .init(x: 0.75, y: 0.75)
        let textUpdate = PhenixTextUpdate(timestamp: 123456789, caption: "Test Caption")
        let windowUpdate = PhenixWindowUpdate(anchorPointOnTextWindow: .init(x: 0.25, y: 0.25))
        XCTAssertNotEqual(sut.configuration.anchorPointOnTextWindow, windowUpdate.anchorPointOnTextWindow?.cgPoint)
        XCTAssertNotEqual(modifiedConfiguration.anchorPointOnTextWindow, windowUpdate.anchorPointOnTextWindow?.cgPoint)

        // When
        sut.update([textUpdate], forWindow: 0) // Create text view
        sut.update(windowUpdate, forWindow: 0) // Update text window
        sut.configuration = modifiedConfiguration // Provide custom configuration

        // Then
        let textView = sut.getWindow(withIndex: 0)
        XCTAssertEqual(textView.configuration.anchorPointOnTextWindow, windowUpdate.anchorPointOnTextWindow?.cgPoint)
    }
}
