import XCTest
@testable import JsonUI
@testable import JsonUICore
#if canImport(SwiftUI)
import SwiftUI
#endif
#if canImport(Combine)
import Combine
#endif

final class JsonUITests: XCTestCase {
    func testModuleLoads() {
        XCTAssertEqual(JsonNode.normalize(type: ":Text"), "Text")
    }

    #if canImport(JavaScriptCore)
    func testJavaScriptCoreEngineBridgesState() throws {
        let json = """
        {"_ui":{"state":{"email":"a@b.co","items":[{"n":1},{"n":2}]},"script":"function twice(x){return x*2;}"},"type":"Text","text":"${twice(state.items.length)}"}
        """
        let actions = JsonActions()
        var invoked: [(String, JsonValue)] = []
        actions.register("tick") { name, args, _ in invoked.append((name, args)); return ["ok": true] }
        let model = JsonUIModel(document: try JsonDocument(json: json), engine: JavaScriptCoreEngine(), actions: actions)
        let ctx = model.context
        XCTAssertTrue(model.runtime.scriptErrors.isEmpty, "\(model.runtime.scriptErrors)")
        XCTAssertEqual(ctx.resolve(model.document.root, "text"), "4")
        XCTAssertEqual(ctx.evaluate("state.email"), "a@b.co")
        XCTAssertEqual(ctx.evaluate("state.items[1].n + 1"), 3)
        ctx.perform(JsonAction.script("state.email = 'x@y.z'; state.count = (state.count || 0) + 5; host.invoke('tick', {by: 5})"))
        XCTAssertEqual(model.store["email"], "x@y.z")
        XCTAssertEqual(model.store["count"], 5)
        XCTAssertEqual(invoked.count, 1)
        XCTAssertEqual(invoked[0].1, ["by": 5])
        XCTAssertEqual(ctx.evaluate("JSON.stringify(host.invoke('tick', 1))"), #"{"ok":true}"#)
        // Locals from ForEach scopes are visible to expressions.
        let scoped = ctx.child(scope: JsonScope(itemName: "item", indexName: "i", basePath: JsonPath("items[1]"), index: 1, item: ["n": 2]))
        XCTAssertEqual(scoped.evaluate("item.n * 10 + i"), 21)
        // Errors are reported, not fatal.
        XCTAssertEqual(ctx.evaluate("undefinedFunction()"), .null)
        XCTAssertFalse(model.runtime.scriptErrors.isEmpty)
    }

    func testModelPublishesChanges() throws {
        let model = try JsonUIModel(json: #"{"_ui":{"state":{"a":1}},"type":"Text","text":"$a"}"#)
        let expectation = expectation(description: "objectWillChange")
        let cancellable = model.objectWillChange.sink { expectation.fulfill() }
        model.store.set(2, at: "a")
        wait(for: [expectation], timeout: 1)
        cancellable.cancel()
        XCTAssertEqual(model.context.resolve(model.document.root, "text"), 2)
    }
    #endif

    #if canImport(SwiftUI)
    struct SampleForm: View {
        @State var email = "a@b.co"
        @State var remember = false
        var body: some View {
            Form {
                Section(header: Text("Account")) {
                    TextField("Email", text: $email)
                    Toggle("Remember me", isOn: $remember)
                }
                Button("Sign in") { }.jsonAction("signIn")
                Text("Hello").font(.headline).padding(8)
            }
        }
    }

    /// Runs under Xcode only: exercises the Mirror based reflector against real SwiftUI views.
    func testReflectsSwiftUIView() throws {
        let reflector = JsonReflector()
        let document = reflector.reflect(SampleForm())
        XCTAssertEqual(document.root.kind, .form, "\(reflector.warnings)")
        let sections = document.root.content
        XCTAssertGreaterThanOrEqual(sections.count, 3)
        let section = sections[0]
        XCTAssertEqual(section.kind, .section)
        XCTAssertEqual(section["header"], "Account")
        let fields = section.content
        XCTAssertEqual(fields.count, 2)
        XCTAssertEqual(fields[0].kind, .textField)
        XCTAssertEqual(fields[0]["title"], "Email")
        XCTAssertEqual(fields[0]["text"], "$email")
        XCTAssertEqual(fields[1].kind, .toggle)
        XCTAssertEqual(fields[1]["isOn"], "$remember")
        XCTAssertEqual(document.header.state["email"], "a@b.co")
        XCTAssertEqual(document.header.state["remember"], false)
        let button = sections[1]
        XCTAssertEqual(button.kind, .button)
        XCTAssertEqual(button["label"], "Sign in")
        XCTAssertEqual(JsonAction(button["action"]), .host(name: "signIn", args: [:]))
        XCTAssertTrue(reflector.actions.contains("signIn"))
        let text = sections[2]
        XCTAssertEqual(text.kind, .text)
        XCTAssertEqual(text["font"], "headline")
        XCTAssertEqual(text["padding"], 8)
        XCTAssertTrue(reflector.warnings.isEmpty, "\(reflector.warnings)")
    }

    func testRegistryNormalizesTypeNames() {
        let registry = JsonViewRegistry()
        registry.register(":MyWidget") { _, _ in Text("x") }
        XCTAssertNotNil(registry.builder(for: "MyWidget"))
        registry.unregister("MyWidget")
        XCTAssertNil(registry.builder(for: "MyWidget"))
    }
    #endif
}
