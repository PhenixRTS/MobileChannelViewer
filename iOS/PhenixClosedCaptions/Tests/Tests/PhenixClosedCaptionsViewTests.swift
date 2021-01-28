//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

@testable import PhenixClosedCaptions
import XCTest

class PhenixClosedCaptionsViewTests: XCTestCase {
    func testViewInitialization() {
        let view = PhenixClosedCaptionsView()
        XCTAssertNotNil(view)
    }

    func testViewDefaultConfiguration() {
        let sut = PhenixClosedCaptionsView()
        XCTAssertEqual(sut.configuration, PhenixClosedCaptionsConfiguration.default)
    }

    func testViewChangeConfiguration() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let customConfiguration = PhenixClosedCaptionsConfiguration(anchorPointOnTextWindow: .zero, positionOfTextWindow: .zero, widthInCharacters: 0, heightInTextLines: 0, textBackgroundColor: .black, textBackgroundAlpha: .zero, visible: true, zOrder: .zero, justify: .center, wordWrap: true)

        // When
        sut.configuration = customConfiguration

        // Then
        XCTAssertEqual(sut.configuration, customConfiguration)
    }

    func testViewCreatesNewTextView() {
        // Given
        let sut = PhenixClosedCaptionsView()
        XCTAssertEqual(sut.windows.count, 0)

        // When
        let _ = sut.getWindow(withIndex: 0)

        // Then
        XCTAssertEqual(sut.windows.count, 1)
    }

    func testViewProvidesDefaultConfigurationToWindow() {
        // Given
        let sut = PhenixClosedCaptionsView()

        // When
        let window = sut.getWindow(withIndex: 0)

        // Then
        XCTAssertEqual(window.configuration.anchorPointOnTextWindow, sut.configuration.anchorPointOnTextWindow)
        XCTAssertEqual(window.configuration.positionOfTextWindow, sut.configuration.positionOfTextWindow)
        XCTAssertEqual(window.configuration.widthInCharacters, sut.configuration.widthInCharacters)
        XCTAssertEqual(window.configuration.heightInTextLines, sut.configuration.heightInTextLines)
        XCTAssertEqual(window.configuration.textBackgroundColor, sut.configuration.textBackgroundColor)
        XCTAssertEqual(window.configuration.textBackgroundAlpha, sut.configuration.textBackgroundAlpha)
        XCTAssertEqual(window.configuration.visible, sut.configuration.visible)
        XCTAssertEqual(window.configuration.zOrder, sut.configuration.zOrder)
        XCTAssertEqual(window.configuration.justify, sut.configuration.justify)
        XCTAssertEqual(window.configuration.wordWrap, sut.configuration.wordWrap)
    }

    func testViewUpdatesWindowBackgroundAlphaParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(backgroundAlpha: 0.25)

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.textBackgroundAlpha, windowUpdate.backgroundAlpha)
    }

    func testViewUpdatesWindowJustifyParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(justify: .left)

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.justify, windowUpdate.justify)
    }

    func testViewUpdatesWindowBackgroundColorParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(backgroundColor: "#00FF00") // Green color
        let backgroundColor = UIColor(hex: windowUpdate.backgroundColor)

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.textBackgroundColor, backgroundColor)
    }

    func testViewUpdatesWindowWordWrapParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(wordWrap: false)

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.wordWrap, windowUpdate.wordWrap)
    }

    func testViewUpdatesWindowZOrderParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(zOrder: 10)

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.zOrder, windowUpdate.zOrder)
    }

    func testViewUpdatesWindowVisibleParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(visible: false)

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.isHidden, windowUpdate.visible == false)
    }

    func testViewUpdatesWindowAnchorPointOnTextWindowParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(anchorPointOnTextWindow: .init(x: 0.25, y: 0.25))

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.anchorPointOnTextWindow, windowUpdate.anchorPointOnTextWindow?.cgPoint)
    }

    func testViewUpdatesWindowPositionOfTextWindowParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(positionOfTextWindow: .init(x: 0.25, y: 0.25))

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.positionOfTextWindow, windowUpdate.positionOfTextWindow?.cgPoint)
    }

    func testViewUpdatesWindowWidthInCharactersParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(widthInCharacters: 100)

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.widthInCharacters, windowUpdate.widthInCharacters)
    }

    func testViewUpdatesWindowHeightInTextLinesParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let windowUpdate = PhenixWindowUpdate(heightInTextLines: 100)

        // When
        sut.update(windowUpdate, forWindow: 0)

        // Then
        XCTAssertEqual(window.configuration.heightInTextLines, windowUpdate.heightInTextLines)
    }

    func testViewUpdatesTextCaptionParameter() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let textUpdate = PhenixTextUpdate(timestamp: 123456789, caption: "Test Caption")

        // When
        sut.update([textUpdate], forWindow: 0)

        // Then
        XCTAssertEqual(window.textView.caption, textUpdate.caption)
    }

    func testViewUpdatesTextParameterBySettingWindowVisibleWhenCaptionIsNotEmpty() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let textUpdate = PhenixTextUpdate(timestamp: 123456789, caption: "Test Caption")

        // When
        sut.update([textUpdate], forWindow: 0)

        // Then
        XCTAssertEqual(window.isHidden, false)
    }

    func testViewUpdatesTextParameterBySettingWindowNotVisibleWhenCaptionIsEmpty() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)
        let textUpdate = PhenixTextUpdate(timestamp: 123456789, caption: "")

        // When
        sut.update([textUpdate], forWindow: 0)

        // Then
        XCTAssertEqual(window.isHidden, true)
    }

    func testViewUpdatesTextParameterBySettingWindowNotVisibleWhenThereAreNoCaptionsProvided() {
        // Given
        let sut = PhenixClosedCaptionsView()
        let window = sut.getWindow(withIndex: 0)

        // When
        sut.update([], forWindow: 0)

        // Then
        XCTAssertEqual(window.isHidden, true)
    }

    func testCaptionTextIsConcatenated() {
        // Given
        let caption1 = "Lorem Ipsum"
        let caption2 = "Foo Bar"

        let sut = PhenixClosedCaptionsView()
        let textUpdates: [PhenixTextUpdate] = [
            PhenixTextUpdate(timestamp: 123456789, caption: caption1),
            PhenixTextUpdate(timestamp: 123456789, caption: caption2),
        ]

        // When
        sut.update(textUpdates, forWindow: 0)

        // Then
        let window = sut.getWindow(withIndex: 0)
        XCTAssertEqual(window.textView.caption, "\(caption1)\(caption2)")
    }

    func testCaptionTextIsConcatenatedIncludingOneNewLineAndSpaces() {
        // Given
        let caption1 = "  Lorem Ipsum  "
        let caption2 = "\nFoo Bar"
        let result = "\(caption1)\(caption2)"

        let sut = PhenixClosedCaptionsView()
        let textUpdates: [PhenixTextUpdate] = [
            PhenixTextUpdate(timestamp: 123456789, caption: caption1),
            PhenixTextUpdate(timestamp: 123456789, caption: caption2),
        ]

        // When
        sut.update(textUpdates, forWindow: 0)

        // Then
        let window = sut.getWindow(withIndex: 0)
        XCTAssertEqual(window.textView.caption, result)
    }

    func testCaptionTextIsConcatenatedIncludingMultipleNewLinesAndSpaces() {
        // Given
        let caption1 = "  Lorem Ipsum  "
        let caption2 = "\n\n\nFoo Bar\n"
        let result = "\(caption1)\(caption2)"

        let sut = PhenixClosedCaptionsView()
        let textUpdates: [PhenixTextUpdate] = [
            PhenixTextUpdate(timestamp: 123456789, caption: caption1),
            PhenixTextUpdate(timestamp: 123456789, caption: caption2),
        ]

        // When
        sut.update(textUpdates, forWindow: 0)

        // Then
        let window = sut.getWindow(withIndex: 0)
        XCTAssertEqual(window.textView.caption, result)
    }
}
