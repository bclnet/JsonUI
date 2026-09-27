import XCTest
@testable import JsonUICore

final class JsonValueTests: XCTestCase {
    func testParseAndSerializeRoundTrip() throws {
        let text = #"{"a":[1,2.5,true,null,"x"],"b":{"c":"d"}}"#
        let value = try JsonValue.parse(text)
        XCTAssertEqual(value["a"][0], 1)
        XCTAssertEqual(value["a"][1], 2.5)
        XCTAssertEqual(value["a"][2], true)
        XCTAssertEqual(value["a"][3], .null)
        XCTAssertEqual(value["a"][4], "x")
        XCTAssertEqual(value["b"]["c"], "d")
        XCTAssertEqual(value.jsonString(), text)
        XCTAssertEqual(try JsonValue.parse(value.jsonString(pretty: true)), value)
    }

    func testBooleansAreNotNumbers() throws {
        let value = try JsonValue.parse(#"{"t":true,"one":1,"zero":0,"f":false}"#)
        XCTAssertEqual(value["t"], .bool(true))
        XCTAssertEqual(value["one"], .number(1))
        XCTAssertEqual(value["zero"], .number(0))
        XCTAssertEqual(value["f"], .bool(false))
    }

    func testConversions() {
        XCTAssertEqual(JsonValue.number(3).stringValue, "3")
        XCTAssertEqual(JsonValue.number(3.25).stringValue, "3.25")
        XCTAssertEqual(JsonValue.string("42").doubleValue, 42)
        XCTAssertEqual(JsonValue.string("yes").boolValue, true)
        XCTAssertEqual(JsonValue.string("").isTruthy, false)
        XCTAssertEqual(JsonValue.array([]).isTruthy, true)
        XCTAssertEqual(JsonValue.null.boolValue, false)
    }

    func testPaths() {
        var value: JsonValue = ["phones": [["number": "1"]]]
        XCTAssertEqual(value.value(at: JsonPath("phones[0].number")), "1")
        XCTAssertEqual(value.value(at: JsonPath("phones.0.number")), "1")
        XCTAssertEqual(value.value(at: JsonPath("phones[3].number")), .null)
        value.setValue("2", at: JsonPath("phones[1].number"))
        XCTAssertEqual(value["phones"][1]["number"], "2")
        value.setValue("Home", at: JsonPath("address.kind"))
        XCTAssertEqual(value["address"]["kind"], "Home")
        XCTAssertEqual(JsonPath("a.b[2].c").description, "a.b[2].c")
    }

    func testEscaping() {
        XCTAssertEqual(JsonValue.string("a\"b\\c\n").jsonString(), #""a\"b\\c\n""#)
    }

    func testCodable() throws {
        let value: JsonValue = ["k": [1, "two", false, .null]]
        let data = try JSONEncoder().encode(value)
        XCTAssertEqual(try JSONDecoder().decode(JsonValue.self, from: data), value)
    }
}
