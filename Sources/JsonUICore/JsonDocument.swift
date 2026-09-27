//
//  JsonDocument.swift
//  JsonUI
//
//  The top level document: an optional `_ui` header (initial state, script,
//  strings) followed by the root node.
//

import Foundation

public struct JsonUIHeader: Equatable, Hashable {
    public static let currentVersion = 1

    public var version: Int
    public var state: [String: JsonValue]
    public var script: String?
    public var strings: [String: String]

    public init(version: Int = JsonUIHeader.currentVersion, state: [String: JsonValue] = [:], script: String? = nil, strings: [String: String] = [:]) {
        self.version = version
        self.state = state
        self.script = script
        self.strings = strings
    }

    public init(value: JsonValue) {
        version = value["version"].intValue ?? JsonUIHeader.currentVersion
        state = value["state"].objectValue ?? [:]
        script = value["script"].stringValue
        strings = (value["strings"].objectValue ?? [:]).compactMapValues { $0.stringValue }
    }

    public var value: JsonValue {
        var object: [String: JsonValue] = ["version": .number(Double(version))]
        if !state.isEmpty { object["state"] = .object(state) }
        if let script = script { object["script"] = .string(script) }
        if !strings.isEmpty { object["strings"] = .object(strings.mapValues { .string($0) }) }
        return .object(object)
    }
}

public enum JsonDocumentError: Error, CustomStringConvertible {
    case notAnObject
    case missingType
    case unsupportedVersion(Int)

    public var description: String {
        switch self {
        case .notAnObject: return "JsonUI: the document must be a JSON object"
        case .missingType: return "JsonUI: the root node has no \"type\""
        case .unsupportedVersion(let v): return "JsonUI: unsupported document version \(v)"
        }
    }
}

public struct JsonDocument: Equatable, Hashable {
    public static let headerKey = "_ui"

    public var header: JsonUIHeader
    public var root: JsonNode

    public init(header: JsonUIHeader = JsonUIHeader(), root: JsonNode) {
        self.header = header
        self.root = root
    }

    public init(value: JsonValue) throws {
        guard case .object(var object) = value else { throw JsonDocumentError.notAnObject }
        let header = object.removeValue(forKey: JsonDocument.headerKey).map { JsonUIHeader(value: $0) } ?? JsonUIHeader()
        guard header.version <= JsonUIHeader.currentVersion else { throw JsonDocumentError.unsupportedVersion(header.version) }
        guard let root = JsonNode(value: .object(object)) else { throw JsonDocumentError.missingType }
        self.header = header
        self.root = root
    }

    public init(data: Data) throws {
        try self.init(value: try JsonValue.parse(data))
    }

    public init(json: String) throws {
        try self.init(value: try JsonValue.parse(json))
    }

    public var value: JsonValue {
        var object = root.props
        object["type"] = .string(root.type)
        object[JsonDocument.headerKey] = header.value
        return .object(object)
    }

    public func jsonString(pretty: Bool = true) -> String {
        value.jsonString(pretty: pretty)
    }
}

extension JsonDocument: Codable {
    public init(from decoder: Decoder) throws {
        try self.init(value: try JsonValue(from: decoder))
    }

    public func encode(to encoder: Encoder) throws {
        try value.encode(to: encoder)
    }
}
