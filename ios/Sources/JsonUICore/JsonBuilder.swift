//
//  JsonBuilder.swift
//  JsonUI
//
//  A small builder DSL for assembling documents in Swift: a form is
//  described with these helpers (or by hand) and serialized with
//  `JsonDocument.jsonString()`.
//
//      let doc = JsonDocument(header: JsonUIHeader(state: ["email": ""])) {
//          JsonNode.form {
//              JsonNode.section("Account") {
//                  JsonNode.textField("Email", text: "$email").keyboard("email")
//                  JsonNode.button("Sign in", action: .host(name: "login", args: [:]))
//              }
//          }
//      }
//

import Foundation

@resultBuilder
public enum JsonNodeBuilder {
    public static func buildBlock(_ components: [JsonNode]...) -> [JsonNode] { components.flatMap { $0 } }
    public static func buildExpression(_ node: JsonNode) -> [JsonNode] { [node] }
    public static func buildExpression(_ nodes: [JsonNode]) -> [JsonNode] { nodes }
    public static func buildOptional(_ component: [JsonNode]?) -> [JsonNode] { component ?? [] }
    public static func buildEither(first component: [JsonNode]) -> [JsonNode] { component }
    public static func buildEither(second component: [JsonNode]) -> [JsonNode] { component }
    public static func buildArray(_ components: [[JsonNode]]) -> [JsonNode] { components.flatMap { $0 } }
}

extension JsonDocument {
    public init(header: JsonUIHeader = JsonUIHeader(), @JsonNodeBuilder content: () -> [JsonNode]) {
        let nodes = content()
        self.init(header: header, root: nodes.count == 1 ? nodes[0] : JsonNode.group { nodes })
    }
}

// MARK: - Views

extension JsonNode {
    static func container(_ kind: Kind, _ props: [String: JsonValue] = [:], content: () -> [JsonNode]) -> JsonNode {
        JsonNode(kind: kind, props: props).withContent(content())
    }

    public static func text(_ text: JsonValue) -> JsonNode { JsonNode(kind: .text, props: ["text": text]) }
    public static func label(_ title: JsonValue, systemImage: String) -> JsonNode { JsonNode(kind: .label, props: ["title": title, "systemImage": .string(systemImage)]) }
    public static func image(systemName: String) -> JsonNode { JsonNode(kind: .image, props: ["systemName": .string(systemName)]) }
    public static func image(name: String) -> JsonNode { JsonNode(kind: .image, props: ["name": .string(name)]) }
    public static func image(url: JsonValue) -> JsonNode { JsonNode(kind: .image, props: ["url": url]) }
    public static func link(_ title: JsonValue, url: JsonValue) -> JsonNode { JsonNode(kind: .link, props: ["title": title, "url": url]) }
    public static func progressView(value: JsonValue? = nil, label: JsonValue? = nil) -> JsonNode {
        var props: [String: JsonValue] = [:]
        if let value = value { props["value"] = value }
        if let label = label { props["label"] = label }
        return JsonNode(kind: .progressView, props: props)
    }

    public static func textField(_ title: JsonValue, text binding: String) -> JsonNode {
        JsonNode(kind: .textField, props: ["title": title, "text": .string(binding)])
    }
    public static func secureField(_ title: JsonValue, text binding: String) -> JsonNode {
        JsonNode(kind: .secureField, props: ["title": title, "text": .string(binding)])
    }
    public static func textEditor(text binding: String) -> JsonNode { JsonNode(kind: .textEditor, props: ["text": .string(binding)]) }
    public static func toggle(_ label: JsonValue, isOn binding: String) -> JsonNode {
        JsonNode(kind: .toggle, props: ["label": label, "isOn": .string(binding)])
    }
    public static func picker(_ label: JsonValue, selection binding: String, options: JsonValue) -> JsonNode {
        JsonNode(kind: .picker, props: ["label": label, "selection": .string(binding), "options": options])
    }
    public static func datePicker(_ label: JsonValue, selection binding: String, components: String = "date") -> JsonNode {
        JsonNode(kind: .datePicker, props: ["label": label, "selection": .string(binding), "components": .string(components)])
    }
    public static func slider(value binding: String, min: Double = 0, max: Double = 1, step: Double? = nil, label: JsonValue? = nil) -> JsonNode {
        var props: [String: JsonValue] = ["value": .string(binding), "min": .number(min), "max": .number(max)]
        if let step = step { props["step"] = .number(step) }
        if let label = label { props["label"] = label }
        return JsonNode(kind: .slider, props: props)
    }
    public static func stepper(_ label: JsonValue, value binding: String, min: Double? = nil, max: Double? = nil, step: Double? = nil) -> JsonNode {
        var props: [String: JsonValue] = ["label": label, "value": .string(binding)]
        if let min = min { props["min"] = .number(min) }
        if let max = max { props["max"] = .number(max) }
        if let step = step { props["step"] = .number(step) }
        return JsonNode(kind: .stepper, props: props)
    }
    public static func button(_ label: JsonValue, action: JsonAction) -> JsonNode {
        JsonNode(kind: .button, props: ["label": label, "action": action.value])
    }
    public static func button(_ label: JsonValue, script: String) -> JsonNode { button(label, action: .script(script)) }
    public static func button(_ label: JsonValue, host name: String, args: [String: JsonValue] = [:]) -> JsonNode {
        button(label, action: .host(name: name, args: args))
    }

    public static func form(@JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode { container(.form, content: content) }
    public static func section(_ header: JsonValue? = nil, footer: JsonValue? = nil, @JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode {
        var props: [String: JsonValue] = [:]
        if let header = header { props["header"] = header }
        if let footer = footer { props["footer"] = footer }
        return container(.section, props, content: content)
    }
    public static func list(@JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode { container(.list, content: content) }
    public static func vstack(alignment: String? = nil, spacing: Double? = nil, @JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode {
        container(.vstack, stackProps(alignment, spacing), content: content)
    }
    public static func hstack(alignment: String? = nil, spacing: Double? = nil, @JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode {
        container(.hstack, stackProps(alignment, spacing), content: content)
    }
    public static func zstack(alignment: String? = nil, @JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode {
        container(.zstack, stackProps(alignment, nil), content: content)
    }
    public static func scrollView(axis: String = "vertical", @JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode {
        container(.scrollView, ["axis": .string(axis)], content: content)
    }
    public static func group(@JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode { container(.group, content: content) }
    public static func navigationView(title: JsonValue? = nil, @JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode {
        container(.navigationView, title.map { ["title": $0] } ?? [:], content: content)
    }
    public static func spacer(minLength: Double? = nil) -> JsonNode {
        JsonNode(kind: .spacer, props: minLength.map { ["minLength": .number($0)] } ?? [:])
    }
    public static func divider() -> JsonNode { JsonNode(kind: .divider) }
    public static func forEach(_ data: JsonValue, item: String = "item", index: String = "index", @JsonNodeBuilder content: () -> [JsonNode]) -> JsonNode {
        container(.forEach, ["data": data, "item": .string(item), "index": .string(index)], content: content)
    }
    public static func `if`(_ condition: JsonValue, @JsonNodeBuilder content: () -> [JsonNode], @JsonNodeBuilder else elseContent: () -> [JsonNode] = { [] }) -> JsonNode {
        var node = container(.conditional, ["condition": condition], content: content)
        let elseNodes = elseContent()
        if !elseNodes.isEmpty { node["else"] = elseNodes.count == 1 ? elseNodes[0].value : .array(elseNodes.map(\.value)) }
        return node
    }
    public static func custom(_ type: String, _ props: [String: JsonValue] = [:], @JsonNodeBuilder content: () -> [JsonNode] = { [] }) -> JsonNode {
        let nodes = content()
        let node = JsonNode(type: type, props: props)
        return nodes.isEmpty ? node : node.withContent(nodes)
    }

    private static func stackProps(_ alignment: String?, _ spacing: Double?) -> [String: JsonValue] {
        var props: [String: JsonValue] = [:]
        if let alignment = alignment { props["alignment"] = .string(alignment) }
        if let spacing = spacing { props["spacing"] = .number(spacing) }
        return props
    }
}

// MARK: - Modifiers

extension JsonNode {
    public func id(_ id: String) -> JsonNode { with("id", .string(id)) }
    public func padding(_ length: Double? = nil) -> JsonNode { with("padding", length.map { .number($0) } ?? .bool(true)) }
    public func padding(_ edges: String, _ length: Double? = nil) -> JsonNode {
        var v: [String: JsonValue] = ["edges": .string(edges)]
        if let length = length { v["length"] = .number(length) }
        return with("padding", .object(v))
    }
    public func padding(top: Double = 0, leading: Double = 0, bottom: Double = 0, trailing: Double = 0) -> JsonNode {
        with("padding", .object(["top": .number(top), "leading": .number(leading), "bottom": .number(bottom), "trailing": .number(trailing)]))
    }
    public func frame(width: Double? = nil, height: Double? = nil, minWidth: Double? = nil, maxWidth: JsonValue? = nil, minHeight: Double? = nil, maxHeight: JsonValue? = nil, alignment: String? = nil) -> JsonNode {
        var v: [String: JsonValue] = [:]
        if let width = width { v["width"] = .number(width) }
        if let height = height { v["height"] = .number(height) }
        if let minWidth = minWidth { v["minWidth"] = .number(minWidth) }
        if let maxWidth = maxWidth { v["maxWidth"] = maxWidth }
        if let minHeight = minHeight { v["minHeight"] = .number(minHeight) }
        if let maxHeight = maxHeight { v["maxHeight"] = maxHeight }
        if let alignment = alignment { v["alignment"] = .string(alignment) }
        return with("frame", .object(v))
    }
    public func font(_ font: JsonValue) -> JsonNode { with("font", font) }
    public func bold(_ value: JsonValue = true) -> JsonNode { with("bold", value) }
    public func italic(_ value: JsonValue = true) -> JsonNode { with("italic", value) }
    public func foregroundColor(_ color: JsonValue) -> JsonNode { with("foregroundColor", color) }
    public func background(_ color: JsonValue) -> JsonNode { with("background", color) }
    public func cornerRadius(_ radius: Double) -> JsonNode { with("cornerRadius", .number(radius)) }
    public func border(_ color: JsonValue, width: Double = 1) -> JsonNode { with("border", .object(["color": color, "width": .number(width)])) }
    public func opacity(_ value: JsonValue) -> JsonNode { with("opacity", value) }
    public func hidden(_ value: JsonValue = true) -> JsonNode { with("hidden", value) }
    public func disabled(_ value: JsonValue = true) -> JsonNode { with("disabled", value) }
    public func onAppear(_ action: JsonAction) -> JsonNode { with("onAppear", action.value) }
    public func onTap(_ action: JsonAction) -> JsonNode { with("onTap", action.value) }
    public func onChange(_ action: JsonAction) -> JsonNode { with("onChange", action.value) }
    public func onCommit(_ action: JsonAction) -> JsonNode { with("onCommit", action.value) }
    public func accessibilityLabel(_ label: String) -> JsonNode { with("accessibilityLabel", .string(label)) }
    public func keyboard(_ keyboard: String) -> JsonNode { with("keyboard", .string(keyboard)) }
    public func autocapitalization(_ value: String) -> JsonNode { with("autocapitalization", .string(value)) }
    public func error(_ message: JsonValue) -> JsonNode { with("error", message) }
    public func style(_ style: JsonValue) -> JsonNode { with("style", style) }
    public func role(_ role: String) -> JsonNode { with("role", .string(role)) }
    public func lineLimit(_ limit: Int) -> JsonNode { with("lineLimit", .number(Double(limit))) }
    public func multilineTextAlignment(_ alignment: String) -> JsonNode { with("multilineTextAlignment", .string(alignment)) }
    /// Appends an ordered modifier (`{"type": "padding", "length": 8}`).
    public func modifier(_ type: String, _ props: [String: JsonValue] = [:]) -> JsonNode {
        var modifiers = self["modifiers"].arrayValue ?? []
        var object = props
        object["type"] = .string(type)
        modifiers.append(.object(object))
        return with("modifiers", .array(modifiers))
    }
}
