import XCTest
@testable import JsonUICore

final class JsonFragmentsTests: XCTestCase {
    let base = URL(string: "https://example.com/forms/login.json")!

    func testReferenceParsing() {
        let same = JsonFragmentReference("#/a/b", base: base)
        XCTAssertNil(same.url)
        XCTAssertEqual(same.pointer, "/a/b")
        XCTAssertEqual(JsonFragmentReference("#header", base: base).pointer, "/_ui/fragments/header")
        let relative = JsonFragmentReference("shared.json#/x", base: base)
        XCTAssertEqual(relative.url?.absoluteString, "https://example.com/forms/shared.json")
        XCTAssertEqual(relative.pointer, "/x")
        XCTAssertEqual(JsonFragmentReference("https://cdn/x.json", base: base).url?.absoluteString, "https://cdn/x.json")
        XCTAssertEqual(JsonFragmentReference("https://cdn/x.json", base: base).pointer, "")
    }

    func testPointer() {
        let v: JsonValue = ["a": ["b": [1, 2, ["c": "d"]]], "e/f": 5, "g~h": 6]
        XCTAssertEqual(JsonFragmentResolver.value(at: "/a/b/2/c", in: v), "d")
        XCTAssertEqual(JsonFragmentResolver.value(at: "/e~1f", in: v), 5)
        XCTAssertEqual(JsonFragmentResolver.value(at: "/g~0h", in: v), 6)
        XCTAssertEqual(JsonFragmentResolver.value(at: "", in: v), v)
        XCTAssertNil(JsonFragmentResolver.value(at: "/a/b/9", in: v))
        XCTAssertNil(JsonFragmentResolver.value(at: "a", in: v))
    }

    func testLocalFragmentsWithOverridesAndSplicing() throws {
        let document: JsonValue = [
            "_ui": ["fragments": [
                "email": ["type": "TextField", "title": "Email", "text": "$email"],
                "buttons": [["type": "Button", "title": "OK"], ["type": "Button", "title": "Cancel"]],
            ]],
            "type": "Form",
            "content": [
                ["$ref": "#email"],
                ["$ref": "#email", "title": "Work email", "text": "$work", "id": nil],
                ["$ref": "#buttons"],
                ["$ref": "#/_ui/fragments/email/title"],
            ],
        ]
        let resolved = try JsonFragmentResolver().resolve(document, base: base)
        let content = resolved["content"].arrayValue!
        XCTAssertEqual(content.count, 5)
        XCTAssertEqual(content[0]["title"], "Email")
        XCTAssertEqual(content[1]["title"], "Work email")
        XCTAssertEqual(content[1]["text"], "$work")
        XCTAssertEqual(content[1]["type"], "TextField")
        XCTAssertEqual(content[2]["title"], "OK")
        XCTAssertEqual(content[3]["title"], "Cancel")
        XCTAssertEqual(content[4], "Email")
        XCTAssertTrue(document.hasFragmentReferences)
        XCTAssertFalse(resolved.hasFragmentReferences)
        let doc = try JsonDocument(value: document, base: base, resolver: JsonFragmentResolver())
        XCTAssertEqual(doc.root.content.count, 4, "the bare string is not a node")
    }

    func testRemoteFragmentsLoadedAndNested() throws {
        let shared: JsonValue = ["sections": ["address": ["type": "Section", "header": "Address", "content": ["$ref": "fields.json#/street"]]]]
        let fields: JsonValue = ["street": ["type": "TextField", "title": "Street"]]
        var loads: [String] = []
        let resolver = JsonFragmentResolver { url in
            loads.append(url.absoluteString)
            switch url.lastPathComponent {
            case "shared.json": return shared
            case "fields.json": return fields
            default: throw JsonFragmentError.missingDocument(url.absoluteString)
            }
        }
        let document: JsonValue = ["type": "Form", "content": [["$ref": "../shared.json#/sections/address"]]]
        let resolved = try resolver.resolve(document, base: base)
        XCTAssertEqual(resolved["content"][0]["content"]["title"], "Street", "the nested ref resolved against shared.json's URL")
        XCTAssertEqual(loads, ["https://example.com/shared.json", "https://example.com/fields.json"])
        // Without a loader the documents must be registered first; externalReferences says which.
        let manual = JsonFragmentResolver()
        XCTAssertEqual(manual.externalReferences(in: document, base: base).map(\.absoluteString), ["https://example.com/shared.json"])
        manual.register(shared, for: URL(string: "https://example.com/shared.json#/ignored")!)
        XCTAssertEqual(manual.externalReferences(in: document, base: base).map(\.absoluteString), ["https://example.com/fields.json"])
        XCTAssertThrowsError(try manual.resolve(document, base: base)) { XCTAssertEqual($0 as? JsonFragmentError, .missingDocument("https://example.com/fields.json")) }
        manual.register(fields, for: URL(string: "https://example.com/fields.json")!)
        XCTAssertEqual(manual.externalReferences(in: document, base: base), [])
        XCTAssertEqual(try manual.resolve(document, base: base), resolved)
    }

    func testErrors() {
        let cyclic: JsonValue = ["_ui": ["fragments": ["a": ["$ref": "#b"], "b": ["$ref": "#a"]]], "type": "Group", "content": ["$ref": "#a"]]
        XCTAssertThrowsError(try JsonFragmentResolver().resolve(cyclic, base: base)) { XCTAssertTrue([JsonFragmentError.cycle("#a"), .cycle("#b")].contains($0 as? JsonFragmentError ?? .tooDeep)) }
        let missing: JsonValue = ["type": "Group", "content": ["$ref": "#nope"]]
        XCTAssertThrowsError(try JsonFragmentResolver().resolve(missing, base: base)) { XCTAssertEqual($0 as? JsonFragmentError, .missingFragment("#nope")) }
    }

    func testSamples() throws {
        let url = Samples.directory.appendingPathComponent("fragments-login.json")
        let resolver = JsonFragmentResolver { url in try JsonValue.parse(try Data(contentsOf: url)) }
        let document = try JsonDocument(value: try JsonValue.parse(try Data(contentsOf: url)), base: url, resolver: resolver)
        XCTAssertEqual(document.root.type, "Form")
        var titles: [String] = []
        for node in document.root.content {
            if node.content.isEmpty { if let t = node["title"].text { titles.append(t) } }
            for child in node.content { if let t = child["title"].text { titles.append(t) } }
        }
        XCTAssertTrue(titles.contains("Email"))
        XCTAssertTrue(titles.contains("Sign in"))
    }

    func testStrictAccessors() {
        XCTAssertEqual(JsonValue.string("a").text, "a")
        XCTAssertNil(JsonValue.number(1).text)
        XCTAssertNil(JsonValue.object([:]).text)
        XCTAssertEqual(JsonValue.number(2).integerValue, 2)
        XCTAssertNil(JsonValue.number(2.5).integerValue)
        XCTAssertNil(JsonValue.string("2").numberValue)
        XCTAssertEqual(JsonValue.bool(true).flag, true)
        XCTAssertNil(JsonValue.string("true").flag)
    }
}
