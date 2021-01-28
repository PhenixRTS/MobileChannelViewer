//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

@testable import PhenixClosedCaptions
import XCTest

class PhenixWindowViewTests: XCTestCase {
    func testViewInitialization() {
        let view = PhenixWindowView()
        XCTAssertNotNil(view)
    }

    func testViewPositionIsTopLeft() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.0, y: 0.0)
        sut.configuration.positionOfTextWindow = .init(x: 0.0, y: 0.0)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.minX.rounded(), superview.frame.minX)
        XCTAssertEqual(sut.frame.minY.rounded(), superview.frame.minY)
    }

    func testViewPositionIsTopCenter() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.5, y: 0.0)
        sut.configuration.positionOfTextWindow = .init(x: 0.5, y: 0.0)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.midX.rounded(), superview.frame.midX)
        XCTAssertEqual(sut.frame.minY.rounded(), superview.frame.minY)
    }

    func testViewPositionIsTopRight() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 1.0, y: 0.0)
        sut.configuration.positionOfTextWindow = .init(x: 1.0, y: 0.0)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.maxX.rounded(), superview.frame.maxX)
        XCTAssertEqual(sut.frame.minY.rounded(), superview.frame.minY)
    }

    func testViewPositionIsMiddleLeft() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.0, y: 0.5)
        sut.configuration.positionOfTextWindow = .init(x: 0.0, y: 0.5)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.minX.rounded(), superview.frame.minX)
        XCTAssertEqual(sut.frame.midY.rounded(), superview.frame.midY)
    }

    func testViewPositionIsMiddleCenter() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.5, y: 0.5)
        sut.configuration.positionOfTextWindow = .init(x: 0.5, y: 0.5)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.midX.rounded(), superview.frame.midX)
        XCTAssertEqual(sut.frame.midY.rounded(), superview.frame.midY)
    }

    func testViewPositionIsMiddleRight() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 1.0, y: 0.5)
        sut.configuration.positionOfTextWindow = .init(x: 1.0, y: 0.5)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.maxX.rounded(), superview.frame.maxX)
        XCTAssertEqual(sut.frame.midY.rounded(), superview.frame.midY)
    }

    func testViewPositionIsBottomLeft() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.0, y: 1.0)
        sut.configuration.positionOfTextWindow = .init(x: 0.0, y: 1.0)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.minX.rounded(), superview.frame.minX)
        XCTAssertEqual(sut.frame.maxY.rounded(), superview.frame.maxY)
    }

    func testViewPositionIsBottomCenter() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.5, y: 1.0)
        sut.configuration.positionOfTextWindow = .init(x: 0.5, y: 1.0)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.midX.rounded(), superview.frame.midX)
        XCTAssertEqual(sut.frame.maxY.rounded(), superview.frame.maxY)
    }

    func testViewPositionIsBottomRight() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 1.0, y: 1.0)
        sut.configuration.positionOfTextWindow = .init(x: 1.0, y: 1.0)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.maxX.rounded(), superview.frame.maxX)
        XCTAssertEqual(sut.frame.maxY.rounded(), superview.frame.maxY)
    }

    func testViewPositionWithCenteredAnchorPoint() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.5, y: 0.5)
        sut.configuration.positionOfTextWindow = .init(x: 0.75, y: 0.75)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.midX.rounded(), superview.frame.maxX * 0.75)
        XCTAssertEqual(sut.frame.midY.rounded(), superview.frame.maxY * 0.75)
    }

    func testViewPositionWithLeftSideAnchorPoint() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.0, y: 0.5)
        sut.configuration.positionOfTextWindow = .init(x: 0.75, y: 0.75)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.minX.rounded(), superview.frame.maxX * 0.75)
        XCTAssertEqual(sut.frame.midY.rounded(), superview.frame.maxY * 0.75)
    }

    func testViewPositionWithRightSideAnchorPoint() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Test")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 1.0, y: 0.5)
        sut.configuration.positionOfTextWindow = .init(x: 0.75, y: 0.75)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.maxX.rounded(), superview.frame.maxX * 0.75)
        XCTAssertEqual(sut.frame.midY.rounded(), superview.frame.maxY * 0.75)
    }

    func testViewPositionIsBottomCenterWithNewLines() {
        // Given
        let superview = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let sut = PhenixWindowView()
        sut.translatesAutoresizingMaskIntoConstraints = false
        sut.set(text: "Lorem\n\n\n\nIpsum")

        // When
        sut.configuration.anchorPointOnTextWindow = .init(x: 0.5, y: 1.0)
        sut.configuration.positionOfTextWindow = .init(x: 0.5, y: 1.0)
        superview.addSubview(sut)
        superview.layoutIfNeeded()

        // Then
        XCTAssertEqual(sut.frame.midX.rounded(), superview.frame.midX)
        XCTAssertEqual(sut.frame.maxY.rounded(), superview.frame.maxY)
    }

    func testWindowAutomaticallyCreatesTextView() {
        // When
        let sut = PhenixWindowView()

        // Then
        XCTAssertNotNil(sut.textView)
    }

    func testViewUpdatesTextViewBackgroundAlphaParameter() {
        // Given
        let backgroundAlpha: CGFloat = 0.25

        let sut = PhenixWindowView()
        sut.set(text: "test")

        // When
        sut.configuration.textBackgroundAlpha = backgroundAlpha

        // Then
        XCTAssertEqual(sut.textView.backgroundAlpha, backgroundAlpha)
    }

    func testViewUpdatesTextViewJustifyParameter() {
        // Given
        let justify: PhenixWindowUpdate.Justification = .right

        let sut = PhenixWindowView()
        sut.set(text: "test")

        // When
        sut.configuration.justify = justify

        // Then
        XCTAssertEqual(sut.textView.justify, justify)
    }

    func testViewUpdatesTextViewBackgroundColorParameter() {
        // Given
        let backgroundColor: UIColor = .green

        let sut = PhenixWindowView()
        sut.set(text: "test")

        // When
        sut.configuration.textBackgroundColor = backgroundColor

        // Then
        XCTAssertEqual(sut.textView.backgroundColor, backgroundColor)
    }

    func testViewUpdatesTextViewWordWrapParameter() {
        // Given
        let wordWrap: Bool = false

        let sut = PhenixWindowView()
        sut.set(text: "test")

        // When
        sut.configuration.wordWrap = wordWrap

        // Then
        XCTAssertEqual(sut.textView.wordWrap, wordWrap)
    }

    func testViewUpdatesTextViewZOrderParameter() {
        // Given
        let zOrder: Int = 20

        let sut = PhenixWindowView()
        sut.set(text: "test")

        // When
        sut.configuration.zOrder = zOrder

        // Then
        XCTAssertEqual(sut.textView.zOrder, zOrder)
    }
}

