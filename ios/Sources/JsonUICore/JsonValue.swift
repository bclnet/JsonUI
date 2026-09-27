//
//  JsonValue.swift
//  JsonUI
//
//  A dynamically typed JSON value. This replaces the `PType`/`DynaCodable`
//  machinery of SwiftUIJson: instead of reflecting SwiftUI internals, the
//  library works on a plain JSON tree that both platforms share.
//

import Foundation

public enum JsonValue: Equatable, Hashable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([JsonValue])
    case object([String: JsonValue])

    // MARK: - Construction

    public init(_ any: Any?) {
        // JSONSerialization yields NSNumber for both booleans and numbers on
        // Apple platforms, and `NSNumber as? Bool` succeeds for 0/1, so the
        // boolean check has to look at the underlying representation first.
        if let any = any {
            if type(of: any) == Bool.self { self = .bool(any as! Bool); return }
            if let number = any as? NSNumber {
                #if canImport(ObjectiveC)
                let isBool = CFGetTypeID(number) == CFBooleanGetTypeID()
                #else
                let encoding = String(cString: number.objCType)
                let isBool = encoding == "c" || encoding == "B"
                #endif
                self = isBool ? .bool(number.boolValue) : .number(number.doubleValue)
                return
            }
        }
        switch any {
        case nil: self = .null
        case let v as JsonValue: self = v
        case let v as Bool: self = .bool(v)
        case let v as Int: self = .number(Double(v))
        case let v as Int64: self = .number(Double(v))
        case let v as Float: self = .number(Double(v))
        case let v as Double: self = .number(v)
        case let v as String: self = .string(v)
        case let v as [Any?]: self = .array(v.map { JsonValue($0) })
        case let v as [Any]: self = .array(v.map { JsonValue($0) })
        case let v as [String: Any?]: self = .object(v.mapValues { JsonValue($0) })
        case let v as [String: Any]: self = .object(v.mapValues { JsonValue($0) })
        case let v as NSNumber: self = .number(v.doubleValue)
        case is NSNull: self = .null
        default: self = .string(String(describing: any!))
        }
    }

    // MARK: - Accessors

    public var isNull: Bool { if case .null = self { return true } else { return false } }

    public var boolValue: Bool? {
        switch self {
        case .bool(let b): return b
        case .number(let n): return n != 0
        case .string(let s):
            switch s.lowercased() {
            case "true", "yes", "1": return true
            case "false", "no", "0", "": return false
            default: return nil
            }
        case .null: return false
        default: return nil
        }
    }

    public var doubleValue: Double? {
        switch self {
        case .number(let n): return n
        case .bool(let b): return b ? 1 : 0
        case .string(let s): return Double(s.trimmingCharacters(in: .whitespaces))
        default: return nil
        }
    }

    public var intValue: Int? {
        guard let d = doubleValue, d.isFinite else { return nil }
        return Int(d)
    }

    /// The string form used when a value is displayed as text.
    public var stringValue: String? {
        switch self {
        case .string(let s): return s
        case .number(let n): return JsonValue.format(number: n)
        case .bool(let b): return b ? "true" : "false"
        case .null: return nil
        default: return jsonString()
        }
    }

    public var arrayValue: [JsonValue]? { if case .array(let a) = self { return a } else { return nil } }
    public var objectValue: [String: JsonValue]? { if case .object(let o) = self { return o } else { return nil } }

    public subscript(key: String) -> JsonValue {
        get { objectValue?[key] ?? .null }
        set {
            var o = objectValue ?? [:]
            if newValue.isNull { o.removeValue(forKey: key) } else { o[key] = newValue }
            self = .object(o)
        }
    }

    public subscript(index: Int) -> JsonValue {
        get { guard let a = arrayValue, index >= 0, index < a.count else { return .null }; return a[index] }
        set {
            var a = arrayValue ?? []
            while a.count <= index { a.append(.null) }
            a[index] = newValue
            self = .array(a)
        }
    }

    /// The "truthiness" of the value, following JavaScript rules.
    public var isTruthy: Bool {
        switch self {
        case .null: return false
        case .bool(let b): return b
        case .number(let n): return n != 0 && !n.isNaN
        case .string(let s): return !s.isEmpty
        case .array, .object: return true
        }
    }

    static func format(number n: Double) -> String {
        if n.isFinite, n == n.rounded(), abs(n) < 1e15 { return String(Int64(n)) }
        return String(n)
    }

    // MARK: - Paths

    /// Reads the value at a dotted / indexed path such as `phones[0].number`.
    public func value(at path: JsonPath) -> JsonValue {
        var current = self
        for segment in path.segments {
            switch segment {
            case .key(let k): current = current[k]
            case .index(let i): current = current[i]
            }
            if current.isNull { return .null }
        }
        return current
    }

    /// Writes `value` at `path`, creating intermediate objects and arrays.
    public mutating func setValue(_ value: JsonValue, at path: JsonPath) {
        guard let first = path.segments.first else { self = value; return }
        let rest = JsonPath(segments: Array(path.segments.dropFirst()))
        switch first {
        case .key(let k):
            var child = self[k]
            child.setValue(value, at: rest)
            if case .object = self {} else { self = .object([:]) }
            self[k] = child
        case .index(let i):
            var child = self[i]
            child.setValue(value, at: rest)
            if case .array = self {} else { self = .array([]) }
            self[i] = child
        }
    }

    // MARK: - JSON text

    /// Serializes the value to compact JSON text (keys sorted for stable output).
    public func jsonString(pretty: Bool = false) -> String {
        var out = ""
        JsonValue.write(self, to: &out, pretty: pretty, indent: 0)
        return out
    }

    private static func write(_ value: JsonValue, to out: inout String, pretty: Bool, indent: Int) {
        switch value {
        case .null: out += "null"
        case .bool(let b): out += b ? "true" : "false"
        case .number(let n):
            if n.isFinite { out += format(number: n) } else { out += "null" }
        case .string(let s): out += escape(s)
        case .array(let a):
            if a.isEmpty { out += "[]"; return }
            out += "["
            for (i, v) in a.enumerated() {
                if i > 0 { out += "," }
                if pretty { out += "\n" + String(repeating: "  ", count: indent + 1) }
                write(v, to: &out, pretty: pretty, indent: indent + 1)
            }
            if pretty { out += "\n" + String(repeating: "  ", count: indent) }
            out += "]"
        case .object(let o):
            if o.isEmpty { out += "{}"; return }
            out += "{"
            for (i, k) in o.keys.sorted().enumerated() {
                if i > 0 { out += "," }
                if pretty { out += "\n" + String(repeating: "  ", count: indent + 1) }
                out += escape(k) + (pretty ? ": " : ":")
                write(o[k]!, to: &out, pretty: pretty, indent: indent + 1)
            }
            if pretty { out += "\n" + String(repeating: "  ", count: indent) }
            out += "}"
        }
    }

    static func escape(_ s: String) -> String {
        var out = "\""
        for ch in s.unicodeScalars {
            switch ch {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            case "\u{08}": out += "\\b"
            case "\u{0C}": out += "\\f"
            default:
                if ch.value < 0x20 { out += String(format: "\\u%04x", ch.value) } else { out.unicodeScalars.append(ch) }
            }
        }
        return out + "\""
    }

    /// Parses JSON text.
    public static func parse(_ text: String) throws -> JsonValue {
        try parse(Data(text.utf8))
    }

    public static func parse(_ data: Data) throws -> JsonValue {
        let any = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return JsonValue(any)
    }

    /// The Foundation representation (for `JSONSerialization` and bridging).
    public var foundationValue: Any {
        switch self {
        case .null: return NSNull()
        case .bool(let b): return b
        case .number(let n): return n
        case .string(let s): return s
        case .array(let a): return a.map { $0.foundationValue }
        case .object(let o): return o.mapValues { $0.foundationValue }
        }
    }
}

// MARK: - Codable

extension JsonValue: Codable {
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let b = try? container.decode(Bool.self) { self = .bool(b) }
        else if let n = try? container.decode(Double.self) { self = .number(n) }
        else if let s = try? container.decode(String.self) { self = .string(s) }
        else if let a = try? container.decode([JsonValue].self) { self = .array(a) }
        else if let o = try? container.decode([String: JsonValue].self) { self = .object(o) }
        else { throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported JSON value") }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .null: try container.encodeNil()
        case .bool(let b): try container.encode(b)
        case .number(let n): try container.encode(n)
        case .string(let s): try container.encode(s)
        case .array(let a): try container.encode(a)
        case .object(let o): try container.encode(o)
        }
    }
}

// MARK: - Literals

extension JsonValue: ExpressibleByStringLiteral, ExpressibleByIntegerLiteral, ExpressibleByFloatLiteral,
                     ExpressibleByBooleanLiteral, ExpressibleByNilLiteral, ExpressibleByArrayLiteral, ExpressibleByDictionaryLiteral {
    public init(stringLiteral value: String) { self = .string(value) }
    public init(integerLiteral value: Int) { self = .number(Double(value)) }
    public init(floatLiteral value: Double) { self = .number(value) }
    public init(booleanLiteral value: Bool) { self = .bool(value) }
    public init(nilLiteral: ()) { self = .null }
    public init(arrayLiteral elements: JsonValue...) { self = .array(elements) }
    public init(dictionaryLiteral elements: (String, JsonValue)...) { self = .object(Dictionary(elements, uniquingKeysWith: { $1 })) }
}

extension JsonValue: CustomStringConvertible {
    public var description: String { jsonString() }
}
