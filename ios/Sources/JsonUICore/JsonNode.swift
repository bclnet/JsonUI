//
//  JsonNode.swift
//  JsonUI
//
//  A node of the form definition: `{ "type": "TextField", "title": "Email", "text": "$email" }`.
//

import Foundation

public struct JsonNode: Equatable, Hashable {
    /// Well known node types. Custom types are looked up in the host's view registry.
    public enum Kind: String, CaseIterable {
        case text = "Text", label = "Label", image = "Image", link = "Link", progressView = "ProgressView"
        case textField = "TextField", secureField = "SecureField", textEditor = "TextEditor"
        case toggle = "Toggle", picker = "Picker", datePicker = "DatePicker", slider = "Slider", stepper = "Stepper", button = "Button"
        case form = "Form", section = "Section", list = "List", vstack = "VStack", hstack = "HStack", zstack = "ZStack"
        case scrollView = "ScrollView", group = "Group", navigationView = "NavigationView", spacer = "Spacer", divider = "Divider"
        case forEach = "ForEach", conditional = "If"
    }

    public var type: String
    public var props: [String: JsonValue]

    public init(type: String, props: [String: JsonValue] = [:]) {
        self.type = JsonNode.normalize(type: type)
        self.props = props
    }

    public init(kind: Kind, props: [String: JsonValue] = [:]) {
        self.init(type: kind.rawValue, props: props)
    }

    /// Strips a leading colon (`":Text"` → `"Text"`), any module prefix (`"SwiftUI.Text"`) and a generic suffix.
    public static func normalize(type: String) -> String {
        var t = type
        if t.hasPrefix(":") { t.removeFirst() }
        if let dot = t.lastIndex(of: "."), !t.hasPrefix("$") { t = String(t[t.index(after: dot)...]) }
        if let generic = t.firstIndex(of: "<") { t = String(t[..<generic]) }
        return t
    }

    public var kind: Kind? { Kind(rawValue: type) }

    public var id: String? { props["id"]?.stringValue }

    public subscript(key: String) -> JsonValue {
        get { props[key] ?? .null }
        set { if newValue.isNull { props.removeValue(forKey: key) } else { props[key] = newValue } }
    }

    public func has(_ key: String) -> Bool { props[key] != nil }

    /// The child nodes of `content` (a single node or an array of nodes).
    public var content: [JsonNode] { nodes(for: "content") }

    /// The nodes stored under `key`, accepting a single node or an array.
    public func nodes(for key: String) -> [JsonNode] {
        JsonNode.nodes(from: self[key])
    }

    /// A single node stored under `key`, if the value is a node object.
    public func node(for key: String) -> JsonNode? {
        JsonNode(value: self[key])
    }

    public static func nodes(from value: JsonValue) -> [JsonNode] {
        switch value {
        case .array(let items): return items.compactMap { JsonNode(value: $0) }
        case .object: return JsonNode(value: value).map { [$0] } ?? []
        default: return []
        }
    }

    /// Builds a node from an object value that has a `type` key.
    public init?(value: JsonValue) {
        guard case .object(let object) = value, let type = object["type"]?.stringValue else { return nil }
        var props = object
        props.removeValue(forKey: "type")
        self.init(type: type, props: props)
    }

    /// The object value of this node, with `type` first when pretty printed.
    public var value: JsonValue {
        var object = props
        object["type"] = .string(type)
        return .object(object)
    }

    // MARK: - Mutation helpers used by the builder DSL

    public func with(_ key: String, _ value: JsonValue) -> JsonNode {
        var copy = self
        copy[key] = value
        return copy
    }

    public func withContent(_ nodes: [JsonNode]) -> JsonNode {
        with("content", nodes.count == 1 ? nodes[0].value : .array(nodes.map(\.value)))
    }
}

extension JsonNode: Codable {
    public init(from decoder: Decoder) throws {
        let value = try JsonValue(from: decoder)
        guard let node = JsonNode(value: value) else {
            throw DecodingError.dataCorrupted(DecodingError.Context(codingPath: decoder.codingPath, debugDescription: "Expected an object with a \"type\" key"))
        }
        self = node
    }

    public func encode(to encoder: Encoder) throws {
        try value.encode(to: encoder)
    }
}

extension JsonNode: CustomStringConvertible {
    public var description: String { value.jsonString(pretty: true) }
}
