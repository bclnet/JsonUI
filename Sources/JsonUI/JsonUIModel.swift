//
//  JsonUIModel.swift
//  JsonUI
//
//  The observable object behind a rendered document, and the root view.
//

#if canImport(SwiftUI) && canImport(Combine)
import SwiftUI
import Combine
import JsonUICore

/// Owns the runtime (state, script engine, host actions) for one document
/// and publishes a change whenever the state changes.
public final class JsonUIModel: ObservableObject {
    public let document: JsonDocument
    public let runtime: JsonRuntime
    public let registry: JsonViewRegistry
    private var listener: UUID?

    /// Creates a model with the default engine for the platform
    /// (JavaScriptCore) and an empty action registry.
    public convenience init(document: JsonDocument, actions: JsonActions = JsonActions(), registry: JsonViewRegistry = .shared, state: [String: JsonValue]? = nil) {
        self.init(document: document, engine: JsonUIModel.defaultEngine(), actions: actions, registry: registry, state: state)
    }

    public init(document: JsonDocument, engine: JsonScriptEngine, actions: JsonActions = JsonActions(), registry: JsonViewRegistry = .shared, state: [String: JsonValue]? = nil) {
        self.document = document
        self.runtime = JsonRuntime(document: document, engine: engine, actions: actions, state: state)
        self.registry = registry
        listener = runtime.store.addListener { [weak self] _ in
            guard let self = self else { return }
            if Thread.isMainThread { self.objectWillChange.send() }
            else { DispatchQueue.main.async { self.objectWillChange.send() } }
        }
    }

    public convenience init(json: String, actions: JsonActions = JsonActions(), registry: JsonViewRegistry = .shared) throws {
        self.init(document: try JsonDocument(json: json), actions: actions, registry: registry)
    }

    deinit {
        if let listener = listener { runtime.store.removeListener(listener) }
    }

    public static func defaultEngine() -> JsonScriptEngine {
        #if canImport(JavaScriptCore)
        return JavaScriptCoreEngine()
        #else
        return NoScriptEngine()
        #endif
    }

    public var context: JsonContext { runtime.context }
    public var store: JsonStore { runtime.store }
    public var actions: JsonActions { runtime.actions }

    /// The current state as a JSON object.
    public var state: JsonValue { runtime.store.snapshot }

    /// Registers a host action.
    public func on(_ name: String, _ handler: @escaping JsonActionHandler) { runtime.actions.register(name, handler) }
    public func on(_ name: String, _ handler: @escaping (JsonValue) -> Void) { runtime.actions.register(name, handler) }
}

/// Renders a JsonUI document.
///
///     JsonUIView(model: try JsonUIModel(json: json))
///
public struct JsonUIView: View {
    @ObservedObject public var model: JsonUIModel

    public init(model: JsonUIModel) {
        self.model = model
    }

    public var body: some View {
        JsonNodeView(node: model.document.root, context: model.context, model: model)
    }
}
#endif
