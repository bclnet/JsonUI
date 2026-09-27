import XCTest
@testable import JsonUICore

final class DynamicValueTests: XCTestCase {
    func testParsing() {
        XCTAssertEqual(DynamicValue(.string("$email")), .binding(JsonPath("email")))
        XCTAssertEqual(DynamicValue(.string("$phone.label")), .binding(JsonPath("phone.label")))
        XCTAssertEqual(DynamicValue(.object(["$bind": "a.b"])), .binding(JsonPath("a.b")))
        XCTAssertEqual(DynamicValue(.string("${state.x > 1}")), .expression("state.x > 1"))
        XCTAssertEqual(DynamicValue(.object(["$expr": "1 + 1"])), .expression("1 + 1"))
        XCTAssertEqual(DynamicValue(.string("@title")), .localized("title"))
        XCTAssertEqual(DynamicValue(.string("$$literal")), .literal("$literal"))
        XCTAssertEqual(DynamicValue(.string("@@literal")), .literal("@literal"))
        XCTAssertEqual(DynamicValue(.string("plain")), .literal("plain"))
        XCTAssertEqual(DynamicValue(.string("$")), .literal("$"))
        XCTAssertEqual(DynamicValue(.number(3)), .literal(3))
        XCTAssertEqual(DynamicValue(.string("Hi ${state.name}!")), .template([.text("Hi "), .expression("state.name"), .text("!")]))
        XCTAssertEqual(DynamicValue(.string("${ f({a:1}) } and ${b}")), .template([.expression("f({a:1})"), .text(" and "), .expression("b")]))
        XCTAssertEqual(DynamicValue(.string("no ${ close")), .literal("no ${ close"))
    }

    func testValueRoundTrip() {
        for s in ["$email", "${state.x}", "@key", "$$literal", "Hi ${a} ${b}", "plain"] {
            XCTAssertEqual(DynamicValue(.string(s)).value, .string(s), s)
        }
    }

    func testActions() {
        XCTAssertEqual(JsonAction(.string("submit")), .host(name: "submit", args: [:]))
        XCTAssertEqual(JsonAction(.string("js: go()")), .script("go()"))
        XCTAssertEqual(JsonAction(.string("JS:go()")), .script("go()"))
        XCTAssertEqual(JsonAction(.object(["script": "a()"])), .script("a()"))
        XCTAssertEqual(JsonAction(.object(["name": "save", "args": ["x": "$x"]])), .host(name: "save", args: ["x": "$x"]))
        XCTAssertEqual(JsonAction(.object(["set": ["step": 2]])), .set(["step": 2]))
        XCTAssertEqual(JsonAction(.array(["a", "js: b()"])), .sequence([.host(name: "a", args: [:]), .script("b()")]))
        XCTAssertNil(JsonAction(.null))
        XCTAssertNil(JsonAction(.string("")))
        for action in [JsonAction.host(name: "a", args: [:]), .host(name: "a", args: ["k": 1]), .script("x()"), .set(["a": 1]), .sequence([.script("y()")])] {
            XCTAssertEqual(JsonAction(action.value), action)
        }
    }
}
