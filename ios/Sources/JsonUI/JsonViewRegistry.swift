//
//  JsonViewRegistry.swift
//  JsonUI
//
//  Host applications register renderers for custom node types here. This is
//  the SwiftUI counterpart of SwiftUIJson's `PType.register`.
//

#if canImport(SwiftUI)
import SwiftUI
import JsonUICore

public final class JsonViewRegistry {
    public typealias Builder = (_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> AnyView

    public static let shared = JsonViewRegistry()

    private var builders: [String: Builder] = [:]

    public init() {}

    public func register(_ type: String, _ builder: @escaping Builder) {
        builders[JsonNode.normalize(type: type)] = builder
    }

    public func register<Content: View>(_ type: String, _ builder: @escaping (_ node: JsonNode, _ context: JsonContext) -> Content) {
        register(type) { node, context, _ in AnyView(builder(node, context)) }
    }

    public func unregister(_ type: String) { builders.removeValue(forKey: JsonNode.normalize(type: type)) }

    public func builder(for type: String) -> Builder? { builders[type] }
}
#endif
