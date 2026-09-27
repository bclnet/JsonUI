//
//  JavaScriptCoreEngine.swift
//  JsonUI
//
//  JavaScriptCore implementation of `JsonScriptEngine`. The form state is
//  exposed through the `__jsonui_host` bridge as JSON strings; the shared
//  prelude (scripts/jsonui-prelude.js) builds the `state` proxy on top of it.
//

#if canImport(JavaScriptCore)
import Foundation
import JavaScriptCore
import JsonUICore

public final class JavaScriptCoreEngine: JsonScriptEngine {
    public let context: JSContext
    private var pendingException: String?
    private weak var host: JsonScriptHost?

    public init(virtualMachine: JSVirtualMachine? = nil) {
        if let virtualMachine = virtualMachine {
            context = JSContext(virtualMachine: virtualMachine)!
        } else {
            context = JSContext()!
        }
        context.name = "JsonUI"
        context.exceptionHandler = { [weak self] _, exception in
            let message = exception?.toString() ?? "unknown error"
            let line = exception?.objectForKeyedSubscript("line")?.toInt32() ?? 0
            self?.pendingException = line > 0 ? "\(message) (line \(line))" : message
        }
    }

    public func attach(host: JsonScriptHost) throws {
        self.host = host
        let bridge = JSValue(newObjectIn: context)!

        let get: @convention(block) (String) -> String = { [weak self] path in self?.host?.scriptGet(path: path) ?? "null" }
        let set: @convention(block) (String, String) -> Void = { [weak self] path, json in self?.host?.scriptSet(path: path, json: json) }
        let has: @convention(block) (String) -> Bool = { [weak self] key in self?.host?.scriptHas(key: key) ?? false }
        let keys: @convention(block) () -> String = { [weak self] in self?.host?.scriptKeys() ?? "[]" }
        let snapshot: @convention(block) () -> String = { [weak self] in self?.host?.scriptSnapshot() ?? "{}" }
        let merge: @convention(block) (String) -> Void = { [weak self] json in self?.host?.scriptMerge(json: json) }
        let invoke: @convention(block) (String, String) -> String = { [weak self] name, args in self?.host?.scriptInvoke(name: name, argsJson: args) ?? "null" }
        let log: @convention(block) (String, String) -> Void = { [weak self] level, message in self?.host?.scriptLog(level: level, message: message) }

        bridge.setObject(get, forKeyedSubscript: "get" as NSString)
        bridge.setObject(set, forKeyedSubscript: "set" as NSString)
        bridge.setObject(has, forKeyedSubscript: "has" as NSString)
        bridge.setObject(keys, forKeyedSubscript: "keys" as NSString)
        bridge.setObject(snapshot, forKeyedSubscript: "snapshot" as NSString)
        bridge.setObject(merge, forKeyedSubscript: "merge" as NSString)
        bridge.setObject(invoke, forKeyedSubscript: "invoke" as NSString)
        bridge.setObject(log, forKeyedSubscript: "log" as NSString)
        context.setObject(bridge, forKeyedSubscript: "__jsonui_host" as NSString)

        _ = try checked { context.evaluateScript(JsonScriptPrelude.source) }
    }

    public func load(script: String) throws {
        _ = try checked { context.evaluateScript(script) }
    }

    public func evaluate(_ expression: String, locals: [String: JsonValue]) throws -> JsonValue {
        let result = try checked {
            context.objectForKeyedSubscript("__jsonui_eval")?.call(withArguments: [expression, JsonValue.object(locals).jsonString()])
        }
        return try JavaScriptCoreEngine.parse(result)
    }

    public func run(_ script: String, locals: [String: JsonValue]) throws {
        _ = try checked {
            context.objectForKeyedSubscript("__jsonui_run")?.call(withArguments: [script, JsonValue.object(locals).jsonString()])
        }
    }

    private func checked(_ body: () -> JSValue?) throws -> JSValue? {
        pendingException = nil
        let value = body()
        if let exception = pendingException {
            pendingException = nil
            throw JsonScriptError.evaluation(exception)
        }
        return value
    }

    private static func parse(_ value: JSValue?) throws -> JsonValue {
        guard let value = value, !value.isUndefined, !value.isNull, let json = value.toString() else { return .null }
        return try JsonValue.parse(json)
    }
}
#endif
