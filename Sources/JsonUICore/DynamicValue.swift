//
//  DynamicValue.swift
//  JsonUI
//
//  A property value that may be a literal, a state binding, a script
//  expression, a template string or a localized string. This replaces the
//  `.var(self)` slot mechanism of SwiftUIJson with an explicit, portable
//  syntax (see docs/SCHEMA.md "Dynamic values").
//

import Foundation

public indirect enum DynamicValue: Equatable {
    case literal(JsonValue)
    case binding(JsonPath)
    case expression(String)
    case template([TemplatePart])
    case localized(String)

    public enum TemplatePart: Equatable {
        case text(String)
        case expression(String)
    }

    // MARK: - Parsing

    public init(_ value: JsonValue) {
        switch value {
        case .string(let s): self = DynamicValue(string: s)
        case .object(let o):
            if o.count == 1, let bind = o["$bind"]?.stringValue { self = .binding(JsonPath(bind)) }
            else if o.count == 1, let expr = o["$expr"]?.stringValue { self = .expression(expr) }
            else { self = .literal(value) }
        default: self = .literal(value)
        }
    }

    public init(string s: String) {
        if s.hasPrefix("$$") || s.hasPrefix("@@") { self = .literal(.string(String(s.dropFirst()))); return }
        if s.hasPrefix("@"), s.count > 1 { self = .localized(String(s.dropFirst())); return }
        if s.hasPrefix("$"), s.count > 1, !s.hasPrefix("${") { self = .binding(JsonPath(String(s.dropFirst()))); return }
        if s.contains("${") {
            let parts = DynamicValue.templateParts(of: s)
            if parts.count == 1, case .expression(let e) = parts[0] { self = .expression(e); return }
            if parts.contains(where: { if case .expression = $0 { return true } else { return false } }) { self = .template(parts); return }
        }
        self = .literal(.string(s))
    }

    /// Splits `"Hello ${state.name}!"` into text and expression parts, honouring nested braces.
    static func templateParts(of s: String) -> [TemplatePart] {
        var parts: [TemplatePart] = []
        var text = ""
        var i = s.startIndex
        while i < s.endIndex {
            if s[i] == "$", s.index(after: i) < s.endIndex, s[s.index(after: i)] == "{" {
                var depth = 0
                var j = s.index(after: i)
                var closed: String.Index?
                while j < s.endIndex {
                    if s[j] == "{" { depth += 1 }
                    else if s[j] == "}" { depth -= 1; if depth == 0 { closed = j; break } }
                    j = s.index(after: j)
                }
                if let closed = closed {
                    if !text.isEmpty { parts.append(.text(text)); text = "" }
                    let exprStart = s.index(i, offsetBy: 2)
                    parts.append(.expression(String(s[exprStart..<closed]).trimmingCharacters(in: .whitespacesAndNewlines)))
                    i = s.index(after: closed)
                    continue
                }
            }
            text.append(s[i])
            i = s.index(after: i)
        }
        if !text.isEmpty { parts.append(.text(text)) }
        return parts
    }

    public var isLiteral: Bool { if case .literal = self { return true } else { return false } }

    public var bindingPath: JsonPath? { if case .binding(let p) = self { return p } else { return nil } }

    /// The value written back to JSON.
    public var value: JsonValue {
        switch self {
        case .literal(let v):
            if case .string(let s) = v, s.hasPrefix("$") || s.hasPrefix("@") { return .string(String(s.first!) + s) }
            return v
        case .binding(let path): return .string("$" + path.description)
        case .expression(let e): return .string("${" + e + "}")
        case .localized(let key): return .string("@" + key)
        case .template(let parts):
            return .string(parts.map { part -> String in
                switch part {
                case .text(let t): return t
                case .expression(let e): return "${" + e + "}"
                }
            }.joined())
        }
    }
}
