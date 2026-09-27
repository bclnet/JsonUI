//
//  ContainerViews.swift
//  JsonUI
//
//  Form, Section, List, stacks, ScrollView, NavigationView, ForEach and If.
//

#if canImport(SwiftUI)
import SwiftUI
import JsonUICore

enum JsonContainerViews {
    /// Renders a list of child nodes inline (inside a `ViewBuilder`).
    @ViewBuilder
    static func children(_ nodes: [JsonNode], _ context: JsonContext, _ model: JsonUIModel) -> some View {
        ForEach(Array(nodes.enumerated()), id: \.offset) { _, child in
            JsonNodeView(node: child, context: context, model: model)
        }
    }

    @ViewBuilder
    static func form(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        Form { children(node.content, context, model) }
    }

    @ViewBuilder
    static func list(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        List { children(node.content, context, model) }
    }

    @ViewBuilder
    static func section(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        Section {
            children(node.content, context, model)
        } header: {
            if node.has("header") { JsonLabelView(value: node["header"], context: context, model: model) }
        } footer: {
            if node.has("footer") { JsonLabelView(value: node["footer"], context: context, model: model) }
        }
    }

    @ViewBuilder
    static func vstack(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        VStack(alignment: JsonStyles.horizontalAlignment(node["alignment"].stringValue), spacing: node["spacing"].doubleValue.map { CGFloat($0) }) {
            children(node.content, context, model)
        }
    }

    @ViewBuilder
    static func hstack(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        HStack(alignment: JsonStyles.verticalAlignment(node["alignment"].stringValue), spacing: node["spacing"].doubleValue.map { CGFloat($0) }) {
            children(node.content, context, model)
        }
    }

    @ViewBuilder
    static func zstack(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        ZStack(alignment: JsonStyles.alignment(node["alignment"].stringValue)) {
            children(node.content, context, model)
        }
    }

    @ViewBuilder
    static func scrollView(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        let horizontal = node["axis"].stringValue == "horizontal"
        ScrollView(horizontal ? .horizontal : .vertical) {
            if horizontal {
                HStack { children(node.content, context, model) }
            } else {
                VStack { children(node.content, context, model) }
            }
        }
    }

    @ViewBuilder
    static func navigationView(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        let title = context.string(node, "title")
        NavigationView {
            Group { children(node.content, context, model) }
                .modifier(NavigationTitleModifier(title: title))
        }
        #if os(iOS)
        .navigationViewStyle(.stack)
        #endif
    }

    struct NavigationTitleModifier: ViewModifier {
        let title: String?
        func body(content: Content) -> some View {
            if let title = title {
                content.navigationTitle(title)
            } else {
                content
            }
        }
    }

    // MARK: - ForEach

    @ViewBuilder
    static func forEach(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        let itemName = node["item"].stringValue ?? "item"
        let indexName = node["index"].stringValue ?? "index"
        let basePath = context.bindingPath(node, "data")
        let items = context.resolve(node, "data").arrayValue ?? []
        let template = node.content
        ForEach(Array(items.enumerated()), id: \.offset) { index, item in
            let scope = JsonScope(itemName: itemName, indexName: indexName, basePath: basePath.map { $0.appending(JsonPath(segments: [.index(index)])) }, index: index, item: item)
            let child = context.child(scope: scope)
            children(template, child, model)
        }
    }

    // MARK: - If

    @ViewBuilder
    static func conditional(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        if context.bool(node, "condition") {
            children(node.content, context, model)
        } else {
            children(node.nodes(for: "else"), context, model)
        }
    }
}
#endif
