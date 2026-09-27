//
//  ScriptEngine.swift
//  JsonUI
//
//  The scripting contract. JavaScriptCore (Apple) and QuickJS (Android)
//  implement it; both install the shared prelude (scripts/jsonui-prelude.js)
//  and expose the form state through a small JSON-string bridge.
//

import Foundation

public enum JsonLogLevel: String {
    case debug, log, info, warn, error
}

public enum JsonScriptError: Error, CustomStringConvertible {
    case evaluation(String)
    case unavailable

    public var description: String {
        switch self {
        case .evaluation(let message): return "JsonUI script error: \(message)"
        case .unavailable: return "JsonUI: no script engine is installed"
        }
    }
}

/// The host side of the script bridge. `JsonContext` implements it; engines
/// call these methods from the `__jsonui_host` object.
public protocol JsonScriptHost: AnyObject {
    func scriptGet(path: String) -> String
    func scriptSet(path: String, json: String)
    func scriptHas(key: String) -> Bool
    func scriptKeys() -> String
    func scriptSnapshot() -> String
    func scriptMerge(json: String)
    func scriptInvoke(name: String, argsJson: String) -> String
    func scriptLog(level: String, message: String)
}

/// A JavaScript engine bound to one form.
public protocol JsonScriptEngine: AnyObject {
    /// Installs the bridge and the prelude. Called once before any other method.
    func attach(host: JsonScriptHost) throws
    /// Loads a document script (`_ui.script`).
    func load(script: String) throws
    /// Evaluates an expression with `locals` in scope and returns its value.
    func evaluate(_ expression: String, locals: [String: JsonValue]) throws -> JsonValue
    /// Runs statements with `locals` in scope.
    func run(_ script: String, locals: [String: JsonValue]) throws
}

extension JsonScriptEngine {
    public func evaluate(_ expression: String) throws -> JsonValue { try evaluate(expression, locals: [:]) }
    public func run(_ script: String) throws { try run(script, locals: [:]) }
}

/// A minimal engine used when no JavaScript engine is available (unit tests,
/// Linux). It resolves `state.a.b`, `local.a`, string/number/boolean literals
/// and simple `!x`, `x === y`, `x !== y` comparisons; anything else is `null`.
public final class NoScriptEngine: JsonScriptEngine {
    private weak var host: JsonScriptHost?

    public init() {}

    public func attach(host: JsonScriptHost) throws { self.host = host }

    public func load(script: String) throws {}

    public func run(_ script: String, locals: [String: JsonValue]) throws {
        // Statements are not supported; assignments of the form `state.x = <expr>` are.
        let statements = script.split(separator: ";").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        for statement in statements where !statement.isEmpty {
            guard let eq = statement.range(of: "=") , statement[statement.index(before: eq.lowerBound)] != "=", statement[eq.upperBound] != "=" else { continue }
            let lhs = statement[..<eq.lowerBound].trimmingCharacters(in: .whitespaces)
            let rhs = statement[eq.upperBound...].trimmingCharacters(in: .whitespaces)
            guard lhs.hasPrefix("state.") else { continue }
            let value = try evaluate(rhs, locals: locals)
            host?.scriptSet(path: String(lhs.dropFirst("state.".count)), json: value.jsonString())
        }
    }

    public func evaluate(_ expression: String, locals: [String: JsonValue]) throws -> JsonValue {
        let expr = expression.trimmingCharacters(in: .whitespacesAndNewlines)
        if expr.isEmpty { return .null }
        if expr.hasPrefix("!") { return .bool(!(try evaluate(String(expr.dropFirst()), locals: locals)).isTruthy) }
        for op in ["===", "!==", "==", "!="] {
            if let range = expr.range(of: " \(op) ") {
                let lhs = try evaluate(String(expr[..<range.lowerBound]), locals: locals)
                let rhs = try evaluate(String(expr[range.upperBound...]), locals: locals)
                let equal = lhs == rhs
                return .bool(op.hasPrefix("!") ? !equal : equal)
            }
        }
        if expr == "true" { return .bool(true) }
        if expr == "false" { return .bool(false) }
        if expr == "null" || expr == "undefined" { return .null }
        if let n = Double(expr) { return .number(n) }
        if expr.count >= 2, let f = expr.first, let l = expr.last, (f == "'" && l == "'") || (f == "\"" && l == "\"") {
            return .string(String(expr.dropFirst().dropLast()))
        }
        if expr.hasPrefix("state.") {
            let path = String(expr.dropFirst("state.".count))
            guard let json = host?.scriptGet(path: path) else { return .null }
            return (try? JsonValue.parse(json)) ?? .null
        }
        if expr == "state" {
            guard let json = host?.scriptSnapshot() else { return .null }
            return (try? JsonValue.parse(json)) ?? .null
        }
        let path = JsonPath(expr)
        if case .key(let first)? = path.first, let local = locals[first] {
            return local.value(at: path.dropFirst())
        }
        return .null
    }
}
