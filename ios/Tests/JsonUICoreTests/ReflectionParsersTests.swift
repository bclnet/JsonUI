import XCTest
@testable import JsonUICore

final class ReflectionParsersTests: XCTestCase {
    func testFontDescriptions() {
        let headline = JsonReflectionParsers.font(fromDescription: "Font(provider: SwiftUI.(unknown context at $1d8ef8a24).TextStyleProvider(textStyle: SwiftUI.Font.TextStyle.headline, design: SwiftUI.Font.Design.default, weight: nil))")
        XCTAssertEqual(headline.value, "headline")
        XCTAssertFalse(headline.bold)

        let system = JsonReflectionParsers.font(fromDescription: "Font(provider: SwiftUI.(unknown context at $1d8).SystemProvider(size: 20.0, weight: Optional(SwiftUI.Font.Weight(value: 0.3)), design: Optional(SwiftUI.Font.Design.rounded)))")
        XCTAssertEqual(system.value, ["size": 20, "weight": "semibold", "design": "rounded"])

        let bold = JsonReflectionParsers.font(fromDescription: "Font(provider: SwiftUI.(unknown context at $1d8).ModifierProvider<SwiftUI.Font.(unknown context at $1d9).BoldModifier>(base: SwiftUI.Font(provider: SwiftUI.(unknown context at $1d8).TextStyleProvider(textStyle: SwiftUI.Font.TextStyle.title, design: SwiftUI.Font.Design.default, weight: nil)), modifier: SwiftUI.Font.(unknown context at $1d9).BoldModifier()))")
        XCTAssertEqual(bold.value, "title")
        XCTAssertTrue(bold.bold)

        let boldWeight = JsonReflectionParsers.font(fromDescription: "SystemProvider(size: 14.0, weight: Optional(SwiftUI.Font.Weight(value: 0.4)), design: nil)")
        XCTAssertEqual(boldWeight.value, ["size": 14])
        XCTAssertTrue(boldWeight.bold)

        let custom = JsonReflectionParsers.font(fromDescription: "Font(provider: SwiftUI.(unknown context at $1d8).NamedProvider(name: \"Avenir\", size: 18.0, textStyle: nil))")
        XCTAssertEqual(custom.value, ["name": "Avenir", "size": 18])

        XCTAssertNil(JsonReflectionParsers.font(fromDescription: "Font(provider: Something())").value)
        XCTAssertEqual(JsonReflectionParsers.weightName(forValue: -0.8), "ultraLight")
        XCTAssertNil(JsonReflectionParsers.weightName(forValue: 0))
    }

    func testColorDescriptions() {
        XCTAssertEqual(JsonReflectionParsers.color(fromDescription: "red"), "red")
        XCTAssertEqual(JsonReflectionParsers.color(fromDescription: "primary"), "primary")
        XCTAssertEqual(JsonReflectionParsers.color(fromDescription: "accentColor"), "accent")
        XCTAssertEqual(JsonReflectionParsers.color(fromDescription: "#FF0000FF"), "#FF0000")
        XCTAssertEqual(JsonReflectionParsers.color(fromDescription: "#3366cc80"), "#803366CC")
        XCTAssertEqual(JsonReflectionParsers.color(fromDescription: "#123456"), "#123456")
        XCTAssertEqual(JsonReflectionParsers.color(fromDescription: "NamedColor(name: \"Brand\", bundle: nil)"), "Brand")
        XCTAssertNil(JsonReflectionParsers.color(fromDescription: "SomeProvider()"))
    }

    func testEdges() {
        XCTAssertEqual(JsonReflectionParsers.edges(fromRawValue: 15), "all")
        XCTAssertEqual(JsonReflectionParsers.edges(fromRawValue: 10), "horizontal")
        XCTAssertEqual(JsonReflectionParsers.edges(fromRawValue: 5), "vertical")
        XCTAssertEqual(JsonReflectionParsers.edges(fromRawValue: 1), "top")
        XCTAssertEqual(JsonReflectionParsers.edges(fromRawValue: 8), "trailing")
        XCTAssertNil(JsonReflectionParsers.edges(fromRawValue: 3))
    }

    func testLocalizedFormatting() {
        XCTAssertEqual(JsonReflectionParsers.format(key: "Hello %@, you have %lld items (%.1f%%)", arguments: ["Sky", "3", "42.5"]), "Hello Sky, you have 3 items (42.5%)")
        XCTAssertEqual(JsonReflectionParsers.format(key: "Plain", arguments: []), "Plain")
        XCTAssertEqual(JsonReflectionParsers.format(key: "%@ and %@", arguments: ["a"]), "a and %@")
    }
}
