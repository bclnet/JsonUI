//
//  JsonAction.swift
//  JsonUI
//
//  An action is data, so it serializes with the document: a host action
//  name, a script, a state assignment or a sequence.
//

import Foundation

public indirect enum JsonAction: Equatable {
    /// Invokes an action registered by the embedding application.
    case host(name: String, args: [String: JsonValue])
    /// Runs JavaScript in the script engine.
    case script(String)
    /// Assigns dynamic values to state keys.
    case set([String: JsonValue])
    /// Runs several actions in order.
    case sequence([JsonAction])

    public static let scriptPrefix = "js:"

    public init?(_ value: JsonValue) {
        switch value {
        case .null: return nil
        case .string(let s):
            let trimmed = s.trimmingCharacters(in: .whitespaces)
            if trimmed.lowercased().hasPrefix(JsonAction.scriptPrefix) {
                self = .script(String(trimmed.dropFirst(JsonAction.scriptPrefix.count)).trimmingCharacters(in: .whitespaces))
            } else if trimmed.isEmpty {
                return nil
            } else {
                self = .host(name: trimmed, args: [:])
            }
        case .array(let items):
            self = .sequence(items.compactMap { JsonAction($0) })
        case .object(let o):
            if let script = o["script"]?.stringValue { self = .script(script) }
            else if let name = o["name"]?.stringValue { self = .host(name: name, args: o["args"]?.objectValue ?? [:]) }
            else if let set = o["set"]?.objectValue { self = .set(set) }
            else { return nil }
        default: return nil
        }
    }

    public var value: JsonValue {
        switch self {
        case .host(let name, let args):
            return args.isEmpty ? .string(name) : .object(["name": .string(name), "args": .object(args)])
        case .script(let s): return .string(JsonAction.scriptPrefix + " " + s)
        case .set(let values): return .object(["set": .object(values)])
        case .sequence(let actions): return .array(actions.map(\.value))
        }
    }
}

/// A host action handler. `args` are already resolved (bindings and
/// expressions evaluated). The returned value is handed back to scripts that
/// used `host.invoke`.
public typealias JsonActionHandler = (_ name: String, _ args: JsonValue, _ context: JsonContext) -> JsonValue?

/// Registry of host actions available to a document.
public final class JsonActions {
    private var handlers: [String: JsonActionHandler] = [:]
    /// Called for action names that have no specific handler.
    public var fallback: JsonActionHandler?

    public init() {}

    public func register(_ name: String, _ handler: @escaping JsonActionHandler) {
        handlers[name] = handler
    }

    public func register(_ name: String, _ handler: @escaping (_ args: JsonValue) -> Void) {
        handlers[name] = { _, args, _ in handler(args); return nil }
    }

    public func unregister(_ name: String) { handlers.removeValue(forKey: name) }

    public func contains(_ name: String) -> Bool { handlers[name] != nil }

    @discardableResult
    public func invoke(_ name: String, args: JsonValue, context: JsonContext) -> JsonValue? {
        if let handler = handlers[name] { return handler(name, args, context) }
        if let fallback = fallback { return fallback(name, args, context) }
        context.log(.warn, "JsonUI: no host action registered for \"\(name)\"")
        return nil
    }
}
