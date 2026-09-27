//
//  JsonNodeView.swift
//  JsonUI
//
//  Renders one node: dispatches on its type, then applies modifiers.
//

#if canImport(SwiftUI)
import SwiftUI
import JsonUICore

public struct JsonNodeView: View {
    public let node: JsonNode
    public let context: JsonContext
    @ObservedObject var model: JsonUIModel

    public init(node: JsonNode, context: JsonContext, model: JsonUIModel) {
        self.node = node
        self.context = context
        self.model = model
    }

    public var body: some View {
        if context.isVisible(node) {
            JsonModifiers.apply(to: content, node: node, context: context)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch node.kind {
        case .text: JsonTextViews.text(node, context)
        case .label: JsonTextViews.label(node, context)
        case .image: JsonTextViews.image(node, context)
        case .link: JsonTextViews.link(node, context)
        case .progressView: JsonTextViews.progress(node, context)
        case .textField: JsonInputViews.textField(node, context, secure: false)
        case .secureField: JsonInputViews.textField(node, context, secure: true)
        case .textEditor: JsonInputViews.textEditor(node, context)
        case .toggle: JsonInputViews.toggle(node, context, model)
        case .picker: JsonInputViews.picker(node, context, model)
        case .datePicker: JsonInputViews.datePicker(node, context)
        case .slider: JsonInputViews.slider(node, context)
        case .stepper: JsonInputViews.stepper(node, context)
        case .button: JsonInputViews.button(node, context, model)
        case .form: JsonContainerViews.form(node, context, model)
        case .section: JsonContainerViews.section(node, context, model)
        case .list: JsonContainerViews.list(node, context, model)
        case .vstack: JsonContainerViews.vstack(node, context, model)
        case .hstack: JsonContainerViews.hstack(node, context, model)
        case .zstack: JsonContainerViews.zstack(node, context, model)
        case .scrollView: JsonContainerViews.scrollView(node, context, model)
        case .group: JsonContainerViews.children(node.content, context, model)
        case .navigationView: JsonContainerViews.navigationView(node, context, model)
        case .spacer: Spacer(minLength: node["minLength"].doubleValue.map { CGFloat($0) })
        case .divider: Divider()
        case .forEach: JsonContainerViews.forEach(node, context, model)
        case .conditional: JsonContainerViews.conditional(node, context, model)
        case nil: custom
        }
    }

    @ViewBuilder
    private var custom: some View {
        if let builder = model.registry.builder(for: node.type) {
            builder(node, context, model)
        } else if node.type == "Unsupported" {
            Text("JsonUI: unsupported view \(node["name"].stringValue ?? "")")
                .font(.caption)
                .foregroundColor(.red)
        } else {
            Text("JsonUI: unknown view \"\(node.type)\"")
                .font(.caption)
                .foregroundColor(.red)
        }
    }
}

/// Convenience for host code: renders a label value that may be a string
/// (dynamic) or a nested node.
struct JsonLabelView: View {
    let value: JsonValue
    let context: JsonContext
    @ObservedObject var model: JsonUIModel

    var body: some View {
        if let node = JsonNode(value: value) {
            JsonNodeView(node: node, context: context, model: model)
        } else {
            Text(context.resolve(value).stringValue ?? "")
        }
    }
}

enum JsonModifiers {
    /// The shorthand modifier properties, in application order.
    static let shorthand = ["padding", "frame", "font", "bold", "italic", "foregroundColor", "background", "cornerRadius", "border", "opacity", "disabled", "onAppear", "onTap", "accessibilityLabel"]

    static func apply<Content: View>(to content: Content, node: JsonNode, context: JsonContext) -> AnyView {
        var view = AnyView(content)
        for modifier in node["modifiers"].arrayValue ?? [] {
            guard let type = modifier["type"].stringValue else { continue }
            view = apply(type, modifier, to: view, node: node, context: context)
        }
        for key in shorthand where node.has(key) {
            // `bold` / `italic` are folded into `font` when both are present.
            if (key == "bold" || key == "italic") && node.has("font") { continue }
            view = apply(key, shorthandProps(key, node), to: view, node: node, context: context)
        }
        return view
    }

    /// Shorthand values are lifted into the same shape as ordered modifiers.
    private static func shorthandProps(_ key: String, _ node: JsonNode) -> JsonValue {
        let value = node[key]
        switch key {
        case "padding":
            if case .object = value { return value }
            if let n = value.doubleValue, value != .bool(true) { return .object(["length": .number(n)]) }
            return .object([:])
        case "font":
            var props: JsonValue = .object(["font": value])
            if node.has("bold") { props["bold"] = node["bold"] }
            if node.has("italic") { props["italic"] = node["italic"] }
            return props
        case "frame", "border": return value
        case "foregroundColor", "background": return .object(["color": value])
        case "cornerRadius": return .object(["radius": value])
        case "opacity": return .object(["value": value])
        case "disabled": return .object(["value": value])
        case "onAppear", "onTap": return .object(["action": value])
        case "accessibilityLabel": return .object(["label": value])
        default: return value
        }
    }

    static func apply(_ type: String, _ props: JsonValue, to view: AnyView, node: JsonNode, context: JsonContext) -> AnyView {
        switch type {
        case "padding":
            let edges = JsonStyles.edges(props["edges"].stringValue)
            if let length = props["length"].doubleValue { return AnyView(view.padding(edges, CGFloat(length))) }
            if props["top"].doubleValue != nil || props["leading"].doubleValue != nil || props["bottom"].doubleValue != nil || props["trailing"].doubleValue != nil {
                let insets = EdgeInsets(top: CGFloat(props["top"].doubleValue ?? 0), leading: CGFloat(props["leading"].doubleValue ?? 0), bottom: CGFloat(props["bottom"].doubleValue ?? 0), trailing: CGFloat(props["trailing"].doubleValue ?? 0))
                return AnyView(view.padding(insets))
            }
            return AnyView(view.padding(edges))
        case "frame":
            let alignment = JsonStyles.alignment(props["alignment"].stringValue)
            let width = JsonStyles.dimension(props["width"].isNull ? nil : props["width"])
            let height = JsonStyles.dimension(props["height"].isNull ? nil : props["height"])
            let hasFlexible = ["minWidth", "maxWidth", "minHeight", "maxHeight"].contains { !props[$0].isNull }
            if hasFlexible {
                return AnyView(view.frame(
                    minWidth: JsonStyles.dimension(props["minWidth"].isNull ? nil : props["minWidth"]),
                    idealWidth: width,
                    maxWidth: JsonStyles.dimension(props["maxWidth"].isNull ? nil : props["maxWidth"]),
                    minHeight: JsonStyles.dimension(props["minHeight"].isNull ? nil : props["minHeight"]),
                    idealHeight: height,
                    maxHeight: JsonStyles.dimension(props["maxHeight"].isNull ? nil : props["maxHeight"]),
                    alignment: alignment))
            }
            return AnyView(view.frame(width: width, height: height, alignment: alignment))
        case "font":
            var font = JsonStyles.font(props["font"], context: context) ?? .body
            if context.resolve(props["bold"]).isTruthy { font = font.bold() }
            if context.resolve(props["italic"]).isTruthy { font = font.italic() }
            return AnyView(view.font(font))
        case "bold":
            guard context.resolve(props).isTruthy else { return view }
            return AnyView(view.font((JsonStyles.font(node["font"], context: context) ?? .body).bold()))
        case "italic":
            guard context.resolve(props).isTruthy else { return view }
            return AnyView(view.font((JsonStyles.font(node["font"], context: context) ?? .body).italic()))
        case "foregroundColor":
            guard let color = JsonStyles.color(props["color"], context: context) else { return view }
            return AnyView(view.foregroundColor(color))
        case "background":
            guard let color = JsonStyles.color(props["color"], context: context) else { return view }
            return AnyView(view.background(color))
        case "cornerRadius":
            let radius = CGFloat(context.resolve(props["radius"]).doubleValue ?? 0)
            return AnyView(view.clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous)))
        case "border":
            let color = JsonStyles.color(props["color"], context: context) ?? .gray
            let width = CGFloat(props["width"].doubleValue ?? 1)
            return AnyView(view.border(color, width: width))
        case "opacity":
            return AnyView(view.opacity(context.resolve(props["value"]).doubleValue ?? 1))
        case "disabled":
            return AnyView(view.disabled(context.resolve(props["value"]).isTruthy))
        case "onAppear":
            guard let action = JsonAction(props["action"]) else { return view }
            return AnyView(view.onAppear { context.perform(action) })
        case "onTap":
            #if os(tvOS)
            return view
            #else
            guard let action = JsonAction(props["action"]) else { return view }
            return AnyView(view.contentShape(Rectangle()).onTapGesture { context.perform(action) })
            #endif
        case "accessibilityLabel":
            guard let label = context.resolve(props["label"]).stringValue else { return view }
            return AnyView(view.accessibilityLabel(Text(label)))
        default:
            context.log(.warn, "JsonUI: unknown modifier \"\(type)\"")
            return view
        }
    }
}
#endif
