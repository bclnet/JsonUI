import XCTest
@testable import JsonUICore

final class JsonContextTests: XCTestCase {
    func testResolvesBindingsTemplatesAndStrings() {
        let doc = JsonDocument(header: JsonUIHeader(state: ["name": "Sky", "count": 2], strings: ["title": "Hello"]), root: .text("x"))
        let runtime = JsonRuntime(document: doc)
        let ctx = runtime.context
        XCTAssertEqual(ctx.resolve(.string("$name")), "Sky")
        XCTAssertEqual(ctx.resolve(.string("Hi ${state.name}, ${state.count}")), "Hi Sky, 2")
        XCTAssertEqual(ctx.resolve(.string("@title")), "Hello")
        XCTAssertEqual(ctx.resolve(.string("@missing")), "missing")
        XCTAssertEqual(ctx.resolve(.string("${state.count === 2}")), true)
        XCTAssertEqual(ctx.resolve(.string("${!state.name}")), false)
        XCTAssertEqual(ctx.resolve(.array(["$name", 1])), ["Sky", 1])
        let node = JsonNode(kind: .text, props: ["hidden": "${state.count === 3}", "disabled": true])
        XCTAssertTrue(ctx.isVisible(node))
        XCTAssertTrue(ctx.isDisabled(node))
        XCTAssertTrue(ctx.child(disabled: true).isDisabled(JsonNode(kind: .text)))
    }

    func testForEachScopeMapsBindingsAndLocals() {
        let runtime = JsonRuntime(state: ["phones": [["label": "Mobile"], ["label": "Home"]]])
        let ctx = runtime.context
        let items = ctx.get(JsonPath("phones")).arrayValue!
        let scope = JsonScope(itemName: "phone", indexName: "i", basePath: JsonPath("phones[1]"), index: 1, item: items[1])
        let child = ctx.child(scope: scope)
        XCTAssertEqual(child.bindingPath(.string("$phone.label")), JsonPath("phones[1].label"))
        XCTAssertEqual(child.resolve(.string("$phone.label")), "Home")
        XCTAssertEqual(child.resolve(.string("${phone.label}")), "Home")
        XCTAssertEqual(child.resolve(.string("#${i}")), "#1")
        child.set("Work", at: JsonPath("phone.label"))
        XCTAssertEqual(runtime.store["phones[1].label"], "Work")
        // A scope without a base path is read only.
        let readOnly = ctx.child(scope: JsonScope(itemName: "opt", indexName: "j", basePath: nil, index: 0, item: ["v": 1]))
        XCTAssertEqual(readOnly.resolve(.string("$opt.v")), 1)
        XCTAssertNil(readOnly.bindingPath(.string("$opt.v")))
    }

    func testActions() {
        let actions = JsonActions()
        var received: [(String, JsonValue)] = []
        actions.register("save") { name, args, _ in received.append((name, args)); return ["ok": true] }
        let runtime = JsonRuntime(actions: actions, state: ["name": "Sky", "step": 1])
        let ctx = runtime.context
        ctx.perform(JsonAction.host(name: "save", args: ["who": "$name", "n": 2]))
        XCTAssertEqual(received.count, 1)
        XCTAssertEqual(received[0].0, "save")
        XCTAssertEqual(received[0].1, ["who": "Sky", "n": 2])
        ctx.perform(JsonAction.set(["step": 2, "copy": "$name"]))
        XCTAssertEqual(runtime.store["step"], 2)
        XCTAssertEqual(runtime.store["copy"], "Sky")
        ctx.perform(JsonAction.sequence([JsonAction.set(["step": 3]), JsonAction.script("state.name = 'X'")]))
        XCTAssertEqual(runtime.store["step"], 3)
        XCTAssertEqual(runtime.store["name"], "X")
        XCTAssertEqual(runtime.scriptInvoke(name: "save", argsJson: #"{"a":1}"#), #"{"ok":true}"#)
        XCTAssertEqual(received.last?.1, ["a": 1])
        let node = JsonNode(kind: .button, props: ["action": ["set": ["step": 9]]])
        ctx.perform(node, "action")
        XCTAssertEqual(runtime.store["step"], 9)
    }

    func testScriptHostBridge() {
        let runtime = JsonRuntime(state: ["a": ["b": 1]])
        XCTAssertEqual(runtime.scriptGet(path: "a.b"), "1")
        XCTAssertEqual(runtime.scriptGet(path: "missing"), "null")
        runtime.scriptSet(path: "a.c", json: #""x""#)
        XCTAssertEqual(runtime.store["a.c"], "x")
        XCTAssertTrue(runtime.scriptHas(key: "a"))
        XCTAssertFalse(runtime.scriptHas(key: "z"))
        XCTAssertEqual(runtime.scriptKeys(), #"["a"]"#)
        runtime.scriptMerge(json: #"{"z":true}"#)
        XCTAssertEqual(runtime.scriptSnapshot(), #"{"a":{"b":1,"c":"x"},"z":true}"#)
    }
}
