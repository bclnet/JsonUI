//
//  JsonReflector.swift
//  JsonUI
//
//  Reflects a live SwiftUI view hierarchy into a JsonUI document, the way
//  SwiftUIJson's encoders did with `Mirror`. Field names of SwiftUI's internal
//  types come from SwiftUIJson (`_tree`, `storage`, `_text`, `__isOn`,
//  `_PaddingLayout.edges`, ...). Because those names are private API they can
//  change between SwiftUI releases, so every lookup is tolerant: anything that
//  cannot be read becomes a warning and an `Unsupported` node rather than a
//  crash.
//
//  Closures (button actions, `onAppear`, stepper increments) cannot be
//  serialized, so they are registered as host actions on the reflector's
//  `JsonActions`, keyed `action1`, `action2`, ... (or the name given with
//  `.jsonAction("save")`). Bindings become state keys, named after the
//  `@State` property they were made from when that can be inferred, or with
//  `.jsonKey("email")`, or `text1`, `isOn2`, ...
//

#if canImport(SwiftUI)
import SwiftUI
import JsonUICore

// MARK: - Hints

/// Names the state key of the next binding found inside the modified view.
public struct JsonKeyModifier: ViewModifier {
    public let key: String
    public init(key: String) { self.key = key }
    public func body(content: Content) -> some View { content }
}

/// Names the host action registered for the next closure found inside the modified view.
public struct JsonActionNameModifier: ViewModifier {
    public let name: String
    public init(name: String) { self.name = name }
    public func body(content: Content) -> some View { content }
}

extension View {
    /// `TextField("Email", text: $email).jsonKey("email")` binds the reflected field to `state.email`.
    public func jsonKey(_ key: String) -> some View { modifier(JsonKeyModifier(key: key)) }
    /// `Button("Save") { ... }.jsonAction("save")` registers the closure as host action `save`.
    public func jsonAction(_ name: String) -> some View { modifier(JsonActionNameModifier(name: name)) }
}

// MARK: - Result

public struct JsonReflection {
    public let document: JsonDocument
    public let actions: JsonActions
    public let warnings: [String]
}

extension JsonDocument {
    /// Reflects `view` into a document. Closures are registered on `actions`.
    public init<V: View>(reflecting view: V, actions: JsonActions = JsonActions()) {
        self = JsonReflector(actions: actions).reflect(view)
    }
}

// MARK: - Reflector

public final class JsonReflector {
    public let actions: JsonActions
    public private(set) var warnings: [String] = []
    public private(set) var state: [String: JsonValue] = [:]

    private var usedKeys: Set<String> = []
    private var pendingKey: String?
    private var pendingActionName: String?
    private var keyCounter = 0
    private var actionCounter = 0
    private var depth = 0
    private let maxDepth = 64

    /// `@State` / `@Binding` properties of the custom views being reflected, innermost last.
    private struct StateProperty {
        let name: String
        let value: Any?
        var used = false
    }
    private var stateScopes: [[StateProperty]] = []

    public init(actions: JsonActions = JsonActions()) {
        self.actions = actions
    }

    public func reflect<V: View>(_ view: V) -> JsonDocument {
        let nodes = nodes(for: view)
        let root = nodes.count == 1 ? nodes[0] : JsonNode(kind: .group).withContent(nodes)
        return JsonDocument(header: JsonUIHeader(state: state), root: root)
    }

    public func reflection<V: View>(_ view: V) -> JsonReflection {
        JsonReflection(document: reflect(view), actions: actions, warnings: warnings)
    }

    // MARK: - Dispatch

    public func nodes(for any: Any) -> [JsonNode] {
        guard let value = JsonReflector.unwrap(any) else { return [] }
        depth += 1
        defer { depth -= 1 }
        if depth > maxDepth {
            warn("view nesting deeper than \(maxDepth); stopping at \(JsonReflector.typeName(value))")
            return []
        }
        let type = JsonReflector.typeName(value)
        switch type {
        case "Text": return [textNode(value)]
        case "TextField", "SecureField": return [textFieldNode(value, secure: type == "SecureField")]
        case "TextEditor": return [textEditorNode(value)]
        case "Toggle": return [toggleNode(value)]
        case "Button": return [buttonNode(value)]
        case "VStack", "LazyVStack": return [stackNode(value, kind: .vstack)]
        case "HStack", "LazyHStack": return [stackNode(value, kind: .hstack)]
        case "ZStack": return [stackNode(value, kind: .zstack)]
        case "Form": return [containerNode(value, kind: .form)]
        case "List": return [containerNode(value, kind: .list)]
        case "Group": return nodes(for: JsonReflector.child(value, "content") as Any)
        case "ScrollView": return [scrollViewNode(value)]
        case "NavigationView", "NavigationStack": return [containerNode(value, kind: .navigationView, contentLabels: ["content", "root"])]
        case "Section": return [sectionNode(value)]
        case "TupleView": return tupleNodes(value)
        case "_ConditionalContent": return conditionalNodes(value)
        case "AnyView": return nodes(for: JsonReflector.descendant(value, "storage", "view") as Any)
        case "EmptyView": return []
        case "Spacer":
            var node = JsonNode(kind: .spacer)
            if let min = JsonReflector.double(JsonReflector.child(value, "minLength")) { node["minLength"] = .number(min) }
            return [node]
        case "Divider": return [JsonNode(kind: .divider)]
        case "Image": return [imageNode(value)]
        case "Label": return [labelNode(value)]
        case "Link": return [linkNode(value)]
        case "Picker": return [pickerNode(value)]
        case "DatePicker": return [datePickerNode(value)]
        case "Slider": return [sliderNode(value)]
        case "Stepper": return stepperNodes(value)
        case "ProgressView": return [progressNode(value)]
        case "ForEach": return [forEachNode(value)]
        case "ModifiedContent": return modifiedNodes(value)
        case "JsonUIView": return jsonUIViewNodes(value)
        case "Optional": return []
        default:
            if let view = value as? any View { return customViewNodes(view) }
            return [unsupported(type)]
        }
    }

    // MARK: - Text

    private func textNode(_ text: Any) -> JsonNode {
        var node = JsonNode.text(.string(textString(text)))
        for modifier in JsonReflector.child(text, "modifiers") as? [Any] ?? [] {
            let mirror = Mirror(reflecting: modifier)
            guard let first = mirror.children.first else {
                if String(describing: modifier) == "italic" { node["italic"] = true }
                continue
            }
            switch first.label {
            case "color":
                if let color = JsonReflector.unwrap(first.value), let value = colorValue(color) { node["foregroundColor"] = value }
            case "font":
                if let font = JsonReflector.unwrap(first.value) { applyFont(font, to: &node) }
            case "weight":
                if let weight = JsonReflector.unwrap(first.value), let name = weightName(weight) { node["font"] = fontMerged(node["font"], weight: name) }
            case "anyTextModifier":
                let name = JsonReflector.typeName(first.value)
                if name.contains("Bold") { node["bold"] = true }
                else if name.contains("Italic") { node["italic"] = true }
                else { warn("ignored text modifier \(name)") }
            default:
                warn("ignored text modifier \(first.label ?? "?")")
            }
        }
        return node
    }

    /// The string of a `Text`, resolving verbatim and localized storage.
    func textString(_ text: Any) -> String {
        guard let storage = JsonReflector.child(text, "storage"), let first = Mirror(reflecting: storage).children.first else {
            return String(describing: text)
        }
        if first.label == "verbatim", let s = first.value as? String { return s }
        let localized = first.value
        if let key = JsonReflector.descendant(localized, "key", "key") as? String ?? JsonReflector.child(localized, "key") as? String {
            let arguments = (JsonReflector.descendant(localized, "key", "arguments") as? [Any] ?? []).map { argumentString($0) }
            return JsonReflectionParsers.format(key: key, arguments: arguments)
        }
        if let s = JsonReflector.firstString(in: localized) { return s }
        warn("could not read Text storage \(JsonReflector.typeName(localized))")
        return ""
    }

    private func argumentString(_ argument: Any) -> String {
        if let storage = JsonReflector.child(argument, "storage"), let first = Mirror(reflecting: storage).children.first {
            if JsonReflector.typeName(first.value) == "Text" { return textString(first.value) }
            if let inner = Mirror(reflecting: first.value).children.first, let value = JsonReflector.unwrap(inner.value) { return JsonValue(value).stringValue ?? String(describing: value) }
        }
        if let value = JsonReflector.unwrap(argument), JsonReflector.typeName(value) == "Text" { return textString(value) }
        return JsonReflector.firstString(in: argument) ?? String(describing: argument)
    }

    // MARK: - Inputs

    private func textFieldNode(_ field: Any, secure: Bool) -> JsonNode {
        var node = JsonNode(kind: secure ? .secureField : .textField)
        if let label = JsonReflector.child(field, "label", "_label") {
            node["title"] = .string(labelString(label))
        } else if let prompt = JsonReflector.unwrap(JsonReflector.child(field, "prompt") as Any), JsonReflector.typeName(prompt) == "Text" {
            node["title"] = .string(textString(prompt))
        }
        if let binding = JsonReflector.firstChild(of: field, typeNamed: "Binding") {
            node["text"] = .string("$" + key(for: "text", binding: binding))
        } else {
            warn("\(secure ? "SecureField" : "TextField") has no text binding")
        }
        if let commit = JsonReflector.child(field, "onCommit") as? () -> Void, let action = registerAction(commit, prefix: "commit") {
            node["onCommit"] = action.value
        }
        return node
    }

    private func textEditorNode(_ editor: Any) -> JsonNode {
        var node = JsonNode(kind: .textEditor)
        if let binding = JsonReflector.firstChild(of: editor, typeNamed: "Binding") {
            node["text"] = .string("$" + key(for: "text", binding: binding))
        }
        return node
    }

    private func toggleNode(_ toggle: Any) -> JsonNode {
        var node = JsonNode(kind: .toggle)
        if let label = JsonReflector.child(toggle, "_label", "label") { node["label"] = labelValue(label) }
        if let binding = JsonReflector.firstChild(of: toggle, typeNamed: "Binding") {
            node["isOn"] = .string("$" + key(for: "isOn", binding: binding))
        } else {
            warn("Toggle has no isOn binding")
        }
        return node
    }

    private func buttonNode(_ button: Any) -> JsonNode {
        var node = JsonNode(kind: .button)
        if let label = JsonReflector.child(button, "_label", "label") { node["label"] = labelValue(label) }
        if let closure = JsonReflector.child(button, "action") as? () -> Void, let action = registerAction(closure, prefix: "action") {
            node["action"] = action.value
        } else {
            warn("Button action could not be read")
        }
        if let role = JsonReflector.unwrap(JsonReflector.child(button, "role") as Any) {
            let description = String(describing: role).lowercased()
            if description.contains("destructive") { node["role"] = "destructive" }
            else if description.contains("cancel") { node["role"] = "cancel" }
        }
        return node
    }

    private func pickerNode(_ picker: Any) -> JsonNode {
        var node = JsonNode(kind: .picker)
        if let label = JsonReflector.child(picker, "label", "_label") { node["label"] = labelValue(label) }
        if let binding = JsonReflector.child(picker, "selection", "_selection") {
            node["selection"] = .string("$" + key(for: "selection", binding: binding))
        }
        let options = nodes(for: JsonReflector.child(picker, "content") as Any).map { option -> JsonValue in
            let label = option.kind == .text ? option["text"] : .string(option["text"].stringValue ?? option["title"].stringValue ?? "")
            let value = option["_tag"].isNull ? label : option["_tag"]
            return .object(["value": value, "label": label])
        }
        node["options"] = .array(options)
        return node
    }

    private func datePickerNode(_ picker: Any) -> JsonNode {
        var node = JsonNode(kind: .datePicker)
        if let label = JsonReflector.child(picker, "label", "_label") { node["label"] = labelValue(label) }
        var components = "date"
        #if !os(tvOS) && !os(watchOS)
        if let displayed = JsonReflector.child(picker, "displayedComponents") as? DatePickerComponents {
            if displayed == [.hourAndMinute] { components = "time" }
            else if displayed.contains(.hourAndMinute) { components = "dateAndTime" }
        }
        #endif
        node["components"] = .string(components)
        if let binding = JsonReflector.child(picker, "selection", "_selection") {
            let name = key(for: "date", binding: binding, transform: { value in
                guard let date = value as? Date else { return JsonValue(value) }
                return .string(JsonReflector.formatDate(date, components: components))
            })
            node["selection"] = .string("$" + name)
        }
        if let min = JsonReflector.unwrap(JsonReflector.child(picker, "minimumDate") as Any) as? Date { node["min"] = .string(JsonReflector.formatDate(min, components: components)) }
        if let max = JsonReflector.unwrap(JsonReflector.child(picker, "maximumDate") as Any) as? Date { node["max"] = .string(JsonReflector.formatDate(max, components: components)) }
        return node
    }

    private func sliderNode(_ slider: Any) -> JsonNode {
        var node = JsonNode(kind: .slider)
        if let label = JsonReflector.child(slider, "label", "_label"), let text = labelStringIfText(label) { node["label"] = .string(text) }
        if let bounds = JsonReflector.firstChild(of: slider, typeNamed: "ClosedRange") {
            if let lower = JsonReflector.double(JsonReflector.child(bounds, "lowerBound")) { node["min"] = .number(lower) }
            if let upper = JsonReflector.double(JsonReflector.child(bounds, "upperBound")) { node["max"] = .number(upper) }
        }
        if let step = JsonReflector.double(JsonReflector.child(slider, "skipDistance", "step")), step > 0, step < 1 || node.has("max") { node["step"] = .number(step) }
        if let binding = JsonReflector.firstChild(of: slider, typeNamed: "Binding") {
            node["value"] = .string("$" + key(for: "value", binding: binding))
        }
        return node
    }

    /// `Stepper` stores increment/decrement closures rather than its binding, so
    /// it is reflected as a label with two buttons wired to those closures.
    private func stepperNodes(_ stepper: Any) -> [JsonNode] {
        var label = JsonNode.text("")
        if let l = JsonReflector.child(stepper, "label", "_label") { label = JsonNode.text(labelValue(l)) }
        let configuration = JsonReflector.child(stepper, "configuration") ?? stepper
        var buttons: [JsonNode] = []
        if let dec = JsonReflector.unwrap(JsonReflector.child(configuration, "onDecrement") as Any) as? () -> Void, let action = registerAction(dec, prefix: "decrement") {
            buttons.append(JsonNode.button("−", action: action).style("bordered"))
        }
        if let inc = JsonReflector.unwrap(JsonReflector.child(configuration, "onIncrement") as Any) as? () -> Void, let action = registerAction(inc, prefix: "increment") {
            buttons.append(JsonNode.button("+", action: action).style("bordered"))
        }
        if buttons.isEmpty { warn("Stepper closures could not be read") }
        return [JsonNode.hstack { [label, JsonNode.spacer()] + buttons }]
    }

    private func progressNode(_ progress: Any) -> JsonNode {
        var node = JsonNode(kind: .progressView)
        if let label = JsonReflector.child(progress, "label", "_label"), let text = labelStringIfText(label) { node["label"] = .string(text) }
        if let value = JsonReflector.double(JsonReflector.unwrap(JsonReflector.child(progress, "fractionCompleted", "value") as Any)) { node["value"] = .number(value) }
        return node
    }

    // MARK: - Containers

    private func containerNode(_ container: Any, kind: JsonNode.Kind, contentLabels: [String] = ["content"]) -> JsonNode {
        let content = contentLabels.lazy.compactMap { JsonReflector.child(container, $0) }.first
        if content == nil { warn("\(kind.rawValue) content could not be read") }
        return JsonNode(kind: kind).withContent(nodes(for: content as Any))
    }

    private func stackNode(_ stack: Any, kind: JsonNode.Kind) -> JsonNode {
        var node = JsonNode(kind: kind)
        let tree = JsonReflector.child(stack, "_tree", "tree") ?? stack
        if let root = JsonReflector.child(tree, "root") {
            if let alignment = JsonReflector.child(root, "alignment") {
                switch kind {
                case .vstack:
                    if let a = alignment as? HorizontalAlignment, a != .center { node["alignment"] = .string(a == .leading ? "leading" : "trailing") }
                case .hstack:
                    if let a = alignment as? VerticalAlignment, a != .center { node["alignment"] = .string(a == .top ? "top" : a == .bottom ? "bottom" : a == .firstTextBaseline ? "firstTextBaseline" : "lastTextBaseline") }
                default:
                    if let a = alignment as? Alignment, a != .center, let name = JsonReflector.alignmentName(a) { node["alignment"] = .string(name) }
                }
            }
            if let spacing = JsonReflector.double(JsonReflector.unwrap(JsonReflector.child(root, "spacing") as Any)) { node["spacing"] = .number(spacing) }
        }
        let content = JsonReflector.child(tree, "content") ?? JsonReflector.child(stack, "content")
        if content == nil { warn("\(kind.rawValue) content could not be read") }
        return node.withContent(nodes(for: content as Any))
    }

    private func scrollViewNode(_ scroll: Any) -> JsonNode {
        var node = JsonNode(kind: .scrollView)
        if let axes = JsonReflector.descendant(scroll, "configuration", "axes") as? Axis.Set, axes == .horizontal { node["axis"] = "horizontal" }
        else if let axes = JsonReflector.child(scroll, "axes") as? Axis.Set, axes == .horizontal { node["axis"] = "horizontal" }
        return node.withContent(nodes(for: JsonReflector.child(scroll, "content") as Any))
    }

    private func sectionNode(_ section: Any) -> JsonNode {
        var node = JsonNode(kind: .section)
        if let header = JsonReflector.unwrap(JsonReflector.child(section, "header") as Any), JsonReflector.typeName(header) != "EmptyView" { node["header"] = labelValue(header) }
        if let footer = JsonReflector.unwrap(JsonReflector.child(section, "footer") as Any), JsonReflector.typeName(footer) != "EmptyView" { node["footer"] = labelValue(footer) }
        return node.withContent(nodes(for: JsonReflector.child(section, "content") as Any))
    }

    private func tupleNodes(_ tuple: Any) -> [JsonNode] {
        let value = JsonReflector.child(tuple, "value") ?? tuple
        return Mirror(reflecting: value).children.flatMap { nodes(for: $0.value) }
    }

    private func conditionalNodes(_ conditional: Any) -> [JsonNode] {
        guard let storage = JsonReflector.child(conditional, "storage"), let first = Mirror(reflecting: storage).children.first else { return [] }
        return nodes(for: first.value)
    }

    private func forEachNode(_ forEach: Any) -> JsonNode {
        var node = JsonNode(kind: .forEach)
        let data = JsonReflector.child(forEach, "data")
        let items: [JsonValue]
        if let array = data as? [Any] { items = array.map { JsonValue($0) } }
        else if let range = data as? Range<Int> { items = range.map { .number(Double($0)) } }
        else if let range = data as? ClosedRange<Int> { items = range.map { .number(Double($0)) } }
        else { items = []; warn("ForEach data \(JsonReflector.typeName(data as Any)) could not be read") }
        node["data"] = .array(items)
        warn("ForEach row content cannot be reflected; a placeholder template was emitted")
        return node.withContent([JsonNode.text("${item}")])
    }

    private func jsonUIViewNodes(_ view: Any) -> [JsonNode] {
        guard let model = JsonReflector.child(view, "_model", "model"), let wrapped = JsonReflector.child(model, "wrappedValue") ?? Optional(model), let m = wrapped as? JsonUIModel else {
            return [unsupported("JsonUIView")]
        }
        for (k, v) in m.document.header.state where state[k] == nil { state[k] = v; usedKeys.insert(k) }
        return [m.document.root]
    }

    private func customViewNodes(_ view: any View) -> [JsonNode] {
        guard let nodes = bodyNodes(of: view) else { return [unsupported(JsonReflector.typeName(view))] }
        return nodes
    }

    private func bodyNodes<V: View>(of view: V) -> [JsonNode]? {
        if V.Body.self == Never.self { return nil }
        var properties: [StateProperty] = []
        for child in Mirror(reflecting: view).children {
            guard let label = child.label, label.hasPrefix("_") else { continue }
            let wrapperType = JsonReflector.typeName(child.value)
            guard ["State", "Binding", "AppStorage", "SceneStorage"].contains(wrapperType) else { continue }
            properties.append(StateProperty(name: String(label.dropFirst()), value: JsonReflector.wrappedValue(of: child.value)))
        }
        stateScopes.append(properties)
        defer { stateScopes.removeLast() }
        return nodes(for: view.body)
    }

    // MARK: - Modifiers

    private func modifiedNodes(_ modified: Any) -> [JsonNode] {
        guard let content = JsonReflector.child(modified, "content"), let modifier = JsonReflector.child(modified, "modifier") else {
            return [unsupported("ModifiedContent")]
        }
        let type = JsonReflector.typeName(modifier)
        switch type {
        case "JsonKeyModifier":
            if let key = JsonReflector.child(modifier, "key") as? String { pendingKey = key }
            return nodes(for: content)
        case "JsonActionNameModifier":
            if let name = JsonReflector.child(modifier, "name") as? String { pendingActionName = name }
            return nodes(for: content)
        default:
            break
        }
        var inner = nodes(for: content)
        guard !inner.isEmpty else { return inner }
        if inner.count > 1 { inner = [JsonNode(kind: .group).withContent(inner)] }
        var node = inner[0]
        apply(modifier, type: type, to: &node)
        return [node]
    }

    private func apply(_ modifier: Any, type: String, to node: inout JsonNode) {
        let fullType = String(describing: Swift.type(of: modifier))
        switch type {
        case "_PaddingLayout":
            var value: JsonValue = .bool(true)
            let edges = (JsonReflector.child(modifier, "edges") as? Edge.Set).flatMap { JsonReflectionParsers.edges(fromRawValue: Int($0.rawValue)) }
            if let insets = JsonReflector.unwrap(JsonReflector.child(modifier, "insets") as Any) as? EdgeInsets {
                if insets.top == insets.leading && insets.top == insets.bottom && insets.top == insets.trailing {
                    value = edges == nil || edges == "all" ? .number(Double(insets.top)) : .object(["edges": .string(edges!), "length": .number(Double(insets.top))])
                } else {
                    value = .object(["top": .number(Double(insets.top)), "leading": .number(Double(insets.leading)), "bottom": .number(Double(insets.bottom)), "trailing": .number(Double(insets.trailing))])
                }
            } else if let edges = edges, edges != "all" {
                value = .object(["edges": .string(edges)])
            }
            set("padding", value, on: &node)
        case "_FrameLayout", "_FlexFrameLayout":
            var frame: [String: JsonValue] = [:]
            for key in ["width", "height", "minWidth", "maxWidth", "minHeight", "maxHeight"] {
                guard let raw = JsonReflector.unwrap(JsonReflector.child(modifier, key) as Any), let n = JsonReflector.double(raw) else { continue }
                frame[key] = n == .infinity ? .string("infinity") : .number(n)
            }
            if let alignment = JsonReflector.child(modifier, "alignment") as? Alignment, alignment != .center, let name = JsonReflector.alignmentName(alignment) { frame["alignment"] = .string(name) }
            if !frame.isEmpty { set("frame", .object(frame), on: &node) }
        case "_BackgroundModifier", "_BackgroundStyleModifier", "_BackgroundShapeModifier":
            if let background = JsonReflector.child(modifier, "background", "style"), let color = colorValue(background) { set("background", color, on: &node) }
            else { warn("only Color backgrounds are reflected (\(fullType))") }
        case "_ForegroundStyleModifier":
            if let style = JsonReflector.child(modifier, "style"), let color = colorValue(style) { set("foregroundColor", color, on: &node) }
            else { warn("only Color foreground styles are reflected") }
        case "_OpacityEffect":
            if let opacity = JsonReflector.double(JsonReflector.child(modifier, "opacity")) { set("opacity", .number(opacity), on: &node) }
        case "_ClipEffect":
            if let shape = JsonReflector.child(modifier, "shape"), JsonReflector.typeName(shape) == "RoundedRectangle", let size = JsonReflector.child(shape, "cornerSize") as? CGSize {
                set("cornerRadius", .number(Double(size.width)), on: &node)
            } else {
                warn("only RoundedRectangle clips are reflected as cornerRadius")
            }
        case "_EnvironmentKeyWritingModifier":
            applyEnvironmentModifier(modifier, fullType: fullType, to: &node)
        case "_AppearanceActionModifier":
            if let appear = JsonReflector.unwrap(JsonReflector.child(modifier, "appear") as Any) as? () -> Void, let action = registerAction(appear, prefix: "appear") {
                set("onAppear", action.value, on: &node)
            }
        case "_TraitWritingModifier":
            if fullType.contains("TagValueTraitKey"), let value = JsonReflector.child(modifier, "value"), let tagged = Mirror(reflecting: value).children.first {
                node["_tag"] = JsonValue(JsonReflector.unwrap(tagged.value)).isNull ? .string(String(describing: tagged.value)) : JsonReflector.scalar(JsonReflector.unwrap(tagged.value)!)
            } else if fullType.contains("IsHiddenTraitKey") || fullType.contains("Hidden") {
                node["hidden"] = true
            } else {
                warn("ignored modifier \(fullType)")
            }
        case "ButtonStyleModifier", "_ButtonStyleModifier", "ButtonStyleContainerModifier":
            if fullType.contains("BorderedProminent") { node["style"] = "borderedProminent" }
            else if fullType.contains("BorderedButtonStyle") { node["style"] = "bordered" }
            else if fullType.contains("PlainButtonStyle") { node["style"] = "plain" }
            else if fullType.contains("Borderless") { node["style"] = "borderless" }
        case "PickerStyleModifier", "_PickerStyleWriter", "PickerStyleWriter":
            if fullType.contains("Segmented") { node["style"] = "segmented" }
            else if fullType.contains("Menu") { node["style"] = "menu" }
            else if fullType.contains("Wheel") { node["style"] = "wheel" }
            else if fullType.contains("Inline") { node["style"] = "inline" }
        case "AccessibilityAttachmentModifier", "_IdentifiedModifier", "__DesignTimeSelectionIdentifier":
            break
        default:
            if fullType.contains("Hidden") { node["hidden"] = true; return }
            warn("ignored modifier \(fullType)")
        }
    }

    private func applyEnvironmentModifier(_ modifier: Any, fullType: String, to node: inout JsonNode) {
        guard let keyPath = JsonReflector.child(modifier, "keyPath") as? AnyKeyPath else { warn("ignored modifier \(fullType)"); return }
        let rawValue = JsonReflector.child(modifier, "value")
        let value = JsonReflector.unwrap(rawValue as Any)
        if keyPath == \EnvironmentValues.font {
            if let font = value { applyFont(font, to: &node) }
        } else if keyPath == \EnvironmentValues.isEnabled {
            if let enabled = value as? Bool, !enabled { set("disabled", true, on: &node) }
        } else if keyPath == \EnvironmentValues.lineLimit {
            if let limit = value as? Int { node["lineLimit"] = .number(Double(limit)) }
        } else if keyPath == \EnvironmentValues.multilineTextAlignment {
            if let alignment = value as? TextAlignment { node["multilineTextAlignment"] = .string(alignment == .leading ? "leading" : alignment == .trailing ? "trailing" : "center") }
        } else if fullType.contains("Color") {
            if let color = value, let colorValue = colorValue(color) { set("foregroundColor", colorValue, on: &node) }
        } else {
            warn("ignored environment modifier \(fullType)")
        }
    }

    /// Sets a shorthand property, falling back to the ordered `modifiers` array when it is already used.
    private func set(_ key: String, _ value: JsonValue, on node: inout JsonNode) {
        if !node.has(key) { node[key] = value; return }
        var props: [String: JsonValue]
        switch key {
        case "padding": props = value.objectValue ?? (value.doubleValue.map { ["length": .number($0)] } ?? [:])
        case "frame", "border": props = value.objectValue ?? [:]
        case "background", "foregroundColor": props = ["color": value]
        case "cornerRadius": props = ["radius": value]
        case "opacity", "disabled": props = ["value": value]
        case "onAppear": props = ["action": value]
        default: props = ["value": value]
        }
        node = node.modifier(key, props)
    }

    private func applyFont(_ font: Any, to node: inout JsonNode) {
        let parsed = JsonReflectionParsers.font(fromDescription: String(describing: font))
        if let value = parsed.value { node["font"] = value } else { warn("could not read font \(String(describing: font).prefix(80))") }
        if parsed.bold { node["bold"] = true }
        if parsed.italic { node["italic"] = true }
    }

    private func fontMerged(_ font: JsonValue, weight: String) -> JsonValue {
        if weight == "bold" { return font }
        var object = font.objectValue ?? (font.stringValue.map { ["style": .string($0)] } ?? [:])
        object["weight"] = .string(weight)
        return .object(object)
    }

    private func weightName(_ weight: Any) -> String? {
        guard let value = JsonReflector.double(JsonReflector.child(weight, "value")) else { return nil }
        return JsonReflectionParsers.weightName(forValue: value) ?? "regular"
    }

    // MARK: - Labels and values

    private func labelValue(_ label: Any) -> JsonValue {
        if let text = labelStringIfText(label) { return .string(text) }
        let nodes = nodes(for: label)
        if nodes.count == 1 { return nodes[0].value }
        if nodes.isEmpty { return .string("") }
        return JsonNode(kind: .hstack).withContent(nodes).value
    }

    private func labelString(_ label: Any) -> String {
        if let text = labelStringIfText(label) { return text }
        return nodes(for: label).compactMap { $0["text"].stringValue ?? $0["title"].stringValue }.joined(separator: " ")
    }

    private func labelStringIfText(_ label: Any) -> String? {
        guard let value = JsonReflector.unwrap(label) else { return nil }
        if JsonReflector.typeName(value) == "Text" { return textString(value) }
        if JsonReflector.typeName(value) == "ModifiedContent", let content = JsonReflector.child(value, "content"), JsonReflector.typeName(content) == "Text" {
            return textString(content)
        }
        return nil
    }

    private func imageNode(_ image: Any) -> JsonNode {
        var node = JsonNode(kind: .image)
        var provider: Any? = JsonReflector.descendant(image, "provider", "base") ?? JsonReflector.child(image, "provider")
        var resizable = false
        var hops = 0
        while let p = provider, hops < 8 {
            hops += 1
            let name = JsonReflector.typeName(p)
            if name.contains("Resizable") { resizable = true }
            if let imageName = JsonReflector.child(p, "name") as? String {
                let description = String(describing: p).lowercased()
                let isSystem = (JsonReflector.child(p, "isSystem") as? Bool) ?? description.contains("system")
                node[isSystem ? "systemName" : "name"] = .string(imageName)
                break
            }
            provider = JsonReflector.child(p, "base").map { JsonReflector.descendant($0, "provider", "base") ?? JsonReflector.child($0, "provider") ?? $0 }
        }
        if !node.has("systemName") && !node.has("name") { warn("Image provider \(JsonReflector.typeName(provider as Any)) is not supported") }
        if resizable { node["resizable"] = true }
        return node
    }

    private func labelNode(_ label: Any) -> JsonNode {
        var node = JsonNode(kind: .label)
        if let title = JsonReflector.child(label, "title") { node["title"] = .string(labelString(title)) }
        if let icon = JsonReflector.child(label, "icon") {
            let iconNodes = nodes(for: icon)
            if let system = iconNodes.first?["systemName"].stringValue { node["systemImage"] = .string(system) }
        }
        return node
    }

    private func linkNode(_ link: Any) -> JsonNode {
        var node = JsonNode(kind: .link)
        if let label = JsonReflector.child(link, "label") { node["title"] = .string(labelString(label)) }
        if let url = JsonReflector.descendant(link, "destination", "configuration", "url") as? URL ?? JsonReflector.child(link, "destination") as? URL {
            node["url"] = .string(url.absoluteString)
        }
        return node
    }

    private func colorValue(_ any: Any) -> JsonValue? {
        guard let value = JsonReflector.unwrap(any) else { return nil }
        guard JsonReflector.typeName(value) == "Color" else { return nil }
        if let parsed = JsonReflectionParsers.color(fromDescription: String(describing: value)) { return parsed }
        warn("could not read color \(String(describing: value).prefix(60))")
        return nil
    }

    // MARK: - Bindings and actions

    /// Registers the binding's current value as initial state and returns the state key.
    private func key(for prefix: String, binding: Any, transform: ((Any) -> JsonValue)? = nil) -> String {
        let current = JsonReflector.wrappedValue(of: binding)
        let value: JsonValue = current.map { transform?($0) ?? JsonReflector.scalar($0) } ?? .null
        let name: String
        if let pending = pendingKey {
            pendingKey = nil
            name = pending
        } else if let matched = matchStateProperty(value: current) {
            name = matched
        } else {
            keyCounter += 1
            name = "\(prefix)\(keyCounter)"
        }
        let unique = uniqueKey(name)
        state[unique] = value
        return unique
    }

    private func matchStateProperty(value: Any?) -> String? {
        let description = value.map { String(describing: $0) } ?? "nil"
        let type = value.map { String(describing: Swift.type(of: $0)) } ?? "nil"
        for scopeIndex in stateScopes.indices.reversed() {
            let candidates = stateScopes[scopeIndex].indices.filter { index in
                let property = stateScopes[scopeIndex][index]
                guard !property.used, !usedKeys.contains(property.name) else { return false }
                let propertyType = property.value.map { String(describing: Swift.type(of: $0)) } ?? "nil"
                return propertyType == type && (property.value.map { String(describing: $0) } ?? "nil") == description
            }
            if let index = candidates.first {
                stateScopes[scopeIndex][index].used = true
                return stateScopes[scopeIndex][index].name
            }
        }
        return nil
    }

    private func uniqueKey(_ name: String) -> String {
        var candidate = name
        var n = 1
        while usedKeys.contains(candidate) { n += 1; candidate = "\(name)\(n)" }
        usedKeys.insert(candidate)
        return candidate
    }

    private func registerAction(_ closure: @escaping () -> Void, prefix: String) -> JsonAction? {
        let name: String
        if let pending = pendingActionName { pendingActionName = nil; name = pending }
        else { actionCounter += 1; name = "\(prefix)\(actionCounter)" }
        actions.register(name) { _ in closure() }
        return .host(name: name, args: [:])
    }

    private func unsupported(_ type: String) -> JsonNode {
        warn("unsupported view \(type)")
        return JsonNode(type: "Unsupported", props: ["name": .string(type)])
    }

    private func warn(_ message: String) {
        warnings.append(message)
    }

    // MARK: - Mirror helpers

    static func typeName(_ any: Any) -> String {
        let full = String(describing: Swift.type(of: any))
        let base = full.split(separator: "<", maxSplits: 1).first.map(String.init) ?? full
        return base.split(separator: ".").last.map(String.init) ?? base
    }

    static func child(_ any: Any, _ labels: String...) -> Any? {
        let children = Mirror(reflecting: any).children
        for label in labels {
            if let match = children.first(where: { $0.label == label }) { return match.value }
        }
        return nil
    }

    static func descendant(_ any: Any, _ path: String...) -> Any? {
        var current: Any = any
        for label in path {
            guard let next = child(current, label), let unwrapped = unwrap(next) else { return nil }
            current = unwrapped
        }
        return current
    }

    static func firstChild(of any: Any, typeNamed name: String) -> Any? {
        Mirror(reflecting: any).children.first { typeName($0.value) == name }?.value
    }

    /// Unwraps optionals (any depth); nil for `.none`.
    static func unwrap(_ any: Any?) -> Any? {
        guard let any = any else { return nil }
        let mirror = Mirror(reflecting: any)
        guard mirror.displayStyle == .optional else { return any }
        guard let first = mirror.children.first else { return nil }
        return unwrap(first.value)
    }

    static func double(_ any: Any?) -> Double? {
        guard let value = unwrap(any) else { return nil }
        switch value {
        case let d as Double: return d
        case let f as Float: return Double(f)
        case let c as CGFloat: return Double(c)
        case let i as Int: return Double(i)
        default: return nil
        }
    }

    /// The current value of a `Binding` / `State` wrapper.
    static func wrappedValue(of wrapper: Any) -> Any? {
        switch wrapper {
        case let b as Binding<String>: return b.wrappedValue
        case let b as Binding<Bool>: return b.wrappedValue
        case let b as Binding<Double>: return b.wrappedValue
        case let b as Binding<Int>: return b.wrappedValue
        case let b as Binding<Float>: return b.wrappedValue
        case let b as Binding<CGFloat>: return b.wrappedValue
        case let b as Binding<Date>: return b.wrappedValue
        case let s as State<String>: return s.wrappedValue
        case let s as State<Bool>: return s.wrappedValue
        case let s as State<Double>: return s.wrappedValue
        case let s as State<Int>: return s.wrappedValue
        case let s as State<Date>: return s.wrappedValue
        default:
            if let value = child(wrapper, "_value", "wrappedValue") { return unwrap(value) }
            return nil
        }
    }

    /// A JSON scalar for a binding value; enums and other types use their description.
    static func scalar(_ value: Any) -> JsonValue {
        switch value {
        case let s as String: return .string(s)
        case let b as Bool: return .bool(b)
        case let d as Double: return .number(d)
        case let i as Int: return .number(Double(i))
        case let f as Float: return .number(Double(f))
        case let c as CGFloat: return .number(Double(c))
        case let date as Date: return .string(formatDate(date, components: "dateAndTime"))
        case let array as [Any]: return .array(array.map { scalar($0) })
        default: return .string(String(describing: value))
        }
    }

    static func firstString(in any: Any) -> String? {
        for child in Mirror(reflecting: any).children {
            if let s = child.value as? String { return s }
            if let unwrapped = unwrap(child.value), let s = firstString(in: unwrapped) { return s }
        }
        return nil
    }

    static func alignmentName(_ alignment: Alignment) -> String? {
        switch alignment {
        case .leading: return "leading"
        case .trailing: return "trailing"
        case .top: return "top"
        case .bottom: return "bottom"
        case .topLeading: return "topLeading"
        case .topTrailing: return "topTrailing"
        case .bottomLeading: return "bottomLeading"
        case .bottomTrailing: return "bottomTrailing"
        default: return nil
        }
    }

    static func formatDate(_ date: Date, components: String) -> String {
        switch components {
        case "date":
            let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.string(from: date)
        case "time":
            let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "HH:mm"; return f.string(from: date)
        default:
            return ISO8601DateFormatter().string(from: date)
        }
    }
}
#endif
