import XCTest
@testable import JsonUICore

final class JsonDocumentTests: XCTestCase {
    func testParsesHeaderAndRoot() throws {
        let doc = try JsonDocument(json: #"{"_ui":{"version":1,"state":{"a":1},"script":"function f(){}","strings":{"k":"v"}},"type":":VStack","content":[{"type":"Text","text":"hi"}]}"#)
        XCTAssertEqual(doc.header.version, 1)
        XCTAssertEqual(doc.header.state, ["a": 1])
        XCTAssertEqual(doc.header.script, "function f(){}")
        XCTAssertEqual(doc.header.strings, ["k": "v"])
        XCTAssertEqual(doc.root.type, "VStack")
        XCTAssertEqual(doc.root.kind, .vstack)
        XCTAssertEqual(doc.root.content.count, 1)
        XCTAssertEqual(doc.root.content[0]["text"], "hi")
        XCTAssertEqual(try JsonDocument(json: doc.jsonString()), doc)
    }

    func testBareNodeAndErrors() throws {
        let doc = try JsonDocument(json: #"{"type":"Text","text":"x"}"#)
        XCTAssertEqual(doc.header.state, [:])
        XCTAssertThrowsError(try JsonDocument(json: "[1]"))
        XCTAssertThrowsError(try JsonDocument(json: #"{"text":"x"}"#))
        XCTAssertThrowsError(try JsonDocument(json: #"{"_ui":{"version":99},"type":"Text"}"#))
    }

    func testNormalizesTypeNames() {
        XCTAssertEqual(JsonNode.normalize(type: ":Text"), "Text")
        XCTAssertEqual(JsonNode.normalize(type: "SwiftUI.TextField<Text>"), "TextField")
        XCTAssertEqual(JsonNode.normalize(type: "MyWidget"), "MyWidget")
    }

    func testSamplesParseAndRender() throws {
        for name in ["login.json", "profile.json", "survey.json"] {
            let doc = try Samples.load(name)
            XCTAssertEqual(doc.header.version, 1, name)
            let runtime = JsonRuntime(document: doc)
            XCTAssertTrue(runtime.scriptErrors.isEmpty, name)
            var count = 0
            func walk(_ node: JsonNode) {
                count += 1
                if node.kind == nil { XCTFail("unknown type \(node.type) in \(name)") }
                node.content.forEach(walk)
                node.nodes(for: "else").forEach(walk)
            }
            walk(doc.root)
            XCTAssertGreaterThan(count, 3, name)
            // Re-encoding and parsing yields the same document.
            XCTAssertEqual(try JsonDocument(json: doc.jsonString()), doc, name)
        }
    }

    func testLoginSampleState() throws {
        let doc = try Samples.load("login.json")
        let ctx = JsonRuntime(document: doc).context
        let form = doc.root.content[0]
        XCTAssertEqual(form.kind, .form)
        let email = form.content[0].content[0]
        XCTAssertEqual(email.kind, .textField)
        XCTAssertEqual(ctx.bindingPath(email, "text"), JsonPath("email"))
        XCTAssertEqual(ctx.string(email, "title"), "Email")
        ctx.set("me@example.com", at: JsonPath("email"))
        XCTAssertEqual(ctx.resolve(email, "text"), "me@example.com")
    }

    func testBuilderDSL() throws {
        let doc = JsonDocument(header: JsonUIHeader(state: ["email": "", "agree": false])) {
            JsonNode.form {
                JsonNode.section("Account") {
                    JsonNode.textField("Email", text: "$email").keyboard("email").id("email")
                    JsonNode.toggle("I agree", isOn: "$agree")
                }
                JsonNode.section {
                    JsonNode.button("Submit", host: "submit", args: ["email": "$email"]).disabled("${!state.agree}").frame(maxWidth: "infinity")
                    JsonNode.if("$agree") {
                        JsonNode.text("Thanks").font("caption").padding(8)
                    } else: {
                        JsonNode.spacer()
                    }
                }
            }
        }
        let json = doc.jsonString()
        let parsed = try JsonDocument(json: json)
        XCTAssertEqual(parsed, doc)
        XCTAssertEqual(parsed.root.kind, .form)
        let sections = parsed.root.content
        XCTAssertEqual(sections.count, 2)
        XCTAssertEqual(sections[0]["header"], "Account")
        XCTAssertEqual(sections[0].content[0]["keyboard"], "email")
        XCTAssertEqual(sections[0].content[0].id, "email")
        let button = sections[1].content[0]
        XCTAssertEqual(JsonAction(button["action"]), .host(name: "submit", args: ["email": "$email"]))
        XCTAssertEqual(button["frame"]["maxWidth"], "infinity")
        let cond = sections[1].content[1]
        XCTAssertEqual(cond.kind, .conditional)
        XCTAssertEqual(cond.content[0]["padding"], 8)
        XCTAssertEqual(cond.nodes(for: "else")[0].kind, .spacer)
        XCTAssertTrue(json.contains("\"_ui\""))
    }
}
