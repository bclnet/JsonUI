//
//  JsonContext.swift
//  JsonUI
//
//  `JsonRuntime` owns everything shared by one rendered document: the state
//  store, the script engine, host actions and strings. `JsonContext` is the
//  per-subtree view of the runtime, carrying the `ForEach` scopes that map
//  item variables (`phone`, `i`) to state paths and script locals.
//

import Foundation

public final class JsonRuntime: JsonScriptHost {
    public let store: JsonStore
    public let engine: JsonScriptEngine
    public let actions: JsonActions
    public var strings: [String: String]
    /// Optional fallback localizer used when a `@key` is not in `strings`.
    public var localizer: ((String) -> String?)?
    public var logger: (JsonLogLevel, String) -> Void = { level, message in
        #if DEBUG
        print("[JsonUI:\(level.rawValue)] \(message)")
        #endif
    }
    /// Errors raised by scripts and expressions, most recent last.
    public private(set) var scriptErrors: [String] = []

    public init(document: JsonDocument, engine: JsonScriptEngine = NoScriptEngine(), actions: JsonActions = JsonActions(), state: [String: JsonValue]? = nil) {
        var initial = document.header.state
        if let state = state { for (k, v) in state { initial[k] = v } }
        self.store = JsonStore(initial)
        self.engine = engine
        self.actions = actions
        self.strings = document.header.strings
        do {
            try engine.attach(host: self)
            if let script = document.header.script, !script.isEmpty {
                try engine.load(script: script)
            }
        } catch {
            report(error)
        }
    }

    public convenience init(engine: JsonScriptEngine = NoScriptEngine(), actions: JsonActions = JsonActions(), state: [String: JsonValue] = [:]) {
        self.init(document: JsonDocument(header: JsonUIHeader(state: state), root: JsonNode(kind: .group)), engine: engine, actions: actions)
    }

    public var context: JsonContext { JsonContext(runtime: self) }

    func report(_ error: Error) {
        let message = "\(error)"
        scriptErrors.append(message)
        logger(.error, message)
    }

    // MARK: - JsonScriptHost

    public func scriptGet(path: String) -> String { store.get(path).jsonString() }
    public func scriptSet(path: String, json: String) { store.set((try? JsonValue.parse(json)) ?? .null, at: path) }
    public func scriptHas(key: String) -> Bool { store.snapshot.objectValue?[key] != nil }
    public func scriptKeys() -> String { JsonValue.array(store.keys.map { .string($0) }).jsonString() }
    public func scriptSnapshot() -> String { store.snapshot.jsonString() }
    public func scriptMerge(json: String) { store.merge((try? JsonValue.parse(json))?.objectValue ?? [:]) }
    public func scriptInvoke(name: String, argsJson: String) -> String {
        let args = (try? JsonValue.parse(argsJson)) ?? .null
        return (actions.invoke(name, args: args, context: context) ?? .null).jsonString()
    }
    public func scriptLog(level: String, message: String) { logger(JsonLogLevel(rawValue: level) ?? .log, message) }
}

public struct JsonScope: Equatable {
    /// Name of the item variable inside the `ForEach` template.
    public var itemName: String
    /// Name of the index variable.
    public var indexName: String
    /// State path of the array element, so `$item.field` bindings write back.
    public var basePath: JsonPath?
    public var index: Int
    public var item: JsonValue

    public init(itemName: String, indexName: String, basePath: JsonPath?, index: Int, item: JsonValue) {
        self.itemName = itemName
        self.indexName = indexName
        self.basePath = basePath
        self.index = index
        self.item = item
    }
}

public final class JsonContext {
    public let runtime: JsonRuntime
    public let scopes: [JsonScope]
    /// Whether an ancestor disabled interaction.
    public let isDisabled: Bool

    public init(runtime: JsonRuntime, scopes: [JsonScope] = [], isDisabled: Bool = false) {
        self.runtime = runtime
        self.scopes = scopes
        self.isDisabled = isDisabled
    }

    public var store: JsonStore { runtime.store }

    public func child(scope: JsonScope) -> JsonContext { JsonContext(runtime: runtime, scopes: scopes + [scope], isDisabled: isDisabled) }

    public func child(disabled: Bool) -> JsonContext { JsonContext(runtime: runtime, scopes: scopes, isDisabled: isDisabled || disabled) }

    public func log(_ level: JsonLogLevel, _ message: String) { runtime.logger(level, message) }

    /// Script locals contributed by the enclosing `ForEach` scopes.
    public var locals: [String: JsonValue] {
        var locals: [String: JsonValue] = [:]
        for scope in scopes {
            locals[scope.itemName] = scope.item
            locals[scope.indexName] = .number(Double(scope.index))
        }
        return locals
    }

    // MARK: - Bindings

    /// Maps a binding path to a state path, replacing `ForEach` item variables
    /// with their element paths (`phone.label` → `phones[0].label`).
    public func statePath(for path: JsonPath) -> JsonPath? {
        guard case .key(let first)? = path.first else { return path }
        for scope in scopes.reversed() where scope.itemName == first {
            guard let base = scope.basePath else { return nil }
            return base.appending(path.dropFirst())
        }
        return path
    }

    /// The state path bound by `value` (`"$email"` / `{"$bind": ...}`), if any.
    public func bindingPath(_ value: JsonValue) -> JsonPath? {
        guard let path = DynamicValue(value).bindingPath else { return nil }
        return statePath(for: path)
    }

    public func bindingPath(_ node: JsonNode, _ key: String) -> JsonPath? { bindingPath(node[key]) }

    public func get(_ path: JsonPath) -> JsonValue {
        if let statePath = statePath(for: path) { return store.get(statePath) }
        // A read-only scope value (a `ForEach` over a computed array).
        guard case .key(let first)? = path.first, let scope = scopes.last(where: { $0.itemName == first }) else { return .null }
        return scope.item.value(at: path.dropFirst())
    }

    public func set(_ value: JsonValue, at path: JsonPath) {
        guard let statePath = statePath(for: path) else {
            log(.warn, "JsonUI: cannot write to read-only scope value \(path)")
            return
        }
        store.set(value, at: statePath)
    }

    // MARK: - Resolution

    /// Resolves a dynamic value to a concrete JSON value.
    public func resolve(_ value: JsonValue) -> JsonValue {
        resolve(DynamicValue(value))
    }

    public func resolve(_ dynamic: DynamicValue) -> JsonValue {
        switch dynamic {
        case .literal(let v):
            switch v {
            case .array(let items): return .array(items.map { resolve($0) })
            case .object(let o): return .object(o.mapValues { resolve($0) })
            default: return v
            }
        case .binding(let path): return get(path)
        case .expression(let expr): return evaluate(expr)
        case .localized(let key): return .string(localized(key))
        case .template(let parts):
            return .string(parts.map { part -> String in
                switch part {
                case .text(let t): return t
                case .expression(let e): return evaluate(e).stringValue ?? ""
                }
            }.joined())
        }
    }

    public func resolve(_ node: JsonNode, _ key: String) -> JsonValue { resolve(node[key]) }

    public func string(_ node: JsonNode, _ key: String) -> String? { resolve(node, key).stringValue }
    public func string(_ node: JsonNode, _ key: String, default defaultValue: String) -> String { string(node, key) ?? defaultValue }
    public func bool(_ node: JsonNode, _ key: String, default defaultValue: Bool = false) -> Bool {
        let v = resolve(node, key)
        return v.isNull ? defaultValue : v.boolValue ?? v.isTruthy
    }
    public func double(_ node: JsonNode, _ key: String) -> Double? { resolve(node, key).doubleValue }
    public func double(_ node: JsonNode, _ key: String, default defaultValue: Double) -> Double { double(node, key) ?? defaultValue }
    public func int(_ node: JsonNode, _ key: String) -> Int? { resolve(node, key).intValue }

    public func localized(_ key: String) -> String {
        runtime.strings[key] ?? runtime.localizer?(key) ?? key
    }

    /// Whether the node should be rendered.
    public func isVisible(_ node: JsonNode) -> Bool { !bool(node, "hidden") }

    /// Whether the node (or an ancestor) is disabled.
    public func isDisabled(_ node: JsonNode) -> Bool { isDisabled || bool(node, "disabled") }

    public func evaluate(_ expression: String) -> JsonValue {
        do {
            return try runtime.engine.evaluate(expression, locals: locals)
        } catch {
            runtime.report(error)
            return .null
        }
    }

    // MARK: - Actions

    public func action(_ node: JsonNode, _ key: String) -> JsonAction? { JsonAction(node[key]) }

    public func hasAction(_ node: JsonNode, _ key: String) -> Bool { action(node, key) != nil }

    /// Performs the action stored under `key` of `node`, if any.
    public func perform(_ node: JsonNode, _ key: String) {
        guard let action = action(node, key) else { return }
        perform(action)
    }

    public func perform(_ action: JsonAction) {
        switch action {
        case .host(let name, let args):
            runtime.actions.invoke(name, args: .object(args.mapValues { resolve($0) }), context: self)
        case .script(let script):
            do { try runtime.engine.run(script, locals: locals) } catch { runtime.report(error) }
        case .set(let values):
            for (key, value) in values { set(resolve(value), at: JsonPath(key)) }
        case .sequence(let actions):
            actions.forEach { perform($0) }
        }
    }
}
