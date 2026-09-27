//
//  InputViews.swift
//  JsonUI
//
//  TextField, SecureField, TextEditor, Toggle, Picker, DatePicker, Slider,
//  Stepper and Button. Input views write through SwiftUI bindings into the
//  form state at the node's bound path.
//

#if canImport(SwiftUI)
import SwiftUI
import JsonUICore

enum JsonBindings {
    static func string(_ node: JsonNode, _ key: String, _ context: JsonContext, onChange: JsonAction? = nil) -> Binding<String> {
        guard let path = context.bindingPath(node, key) else {
            return .constant(context.string(node, key) ?? "")
        }
        return Binding(
            get: { context.get(path).stringValue ?? "" },
            set: { newValue in
                guard context.get(path).stringValue ?? "" != newValue else { return }
                context.set(.string(newValue), at: path)
                if let onChange = onChange { context.perform(onChange) }
            })
    }

    static func bool(_ node: JsonNode, _ key: String, _ context: JsonContext, onChange: JsonAction? = nil) -> Binding<Bool> {
        guard let path = context.bindingPath(node, key) else {
            return .constant(context.bool(node, key))
        }
        return Binding(
            get: { context.get(path).boolValue ?? false },
            set: { newValue in
                guard (context.get(path).boolValue ?? false) != newValue else { return }
                context.set(.bool(newValue), at: path)
                if let onChange = onChange { context.perform(onChange) }
            })
    }

    static func double(_ node: JsonNode, _ key: String, _ context: JsonContext, default defaultValue: Double = 0, onChange: JsonAction? = nil) -> Binding<Double> {
        guard let path = context.bindingPath(node, key) else {
            return .constant(context.double(node, key) ?? defaultValue)
        }
        return Binding(
            get: { context.get(path).doubleValue ?? defaultValue },
            set: { newValue in
                guard (context.get(path).doubleValue ?? defaultValue) != newValue else { return }
                context.set(.number(newValue), at: path)
                if let onChange = onChange { context.perform(onChange) }
            })
    }
}

enum JsonInputViews {
    // MARK: - Text input

    @ViewBuilder
    static func textField(_ node: JsonNode, _ context: JsonContext, secure: Bool) -> some View {
        let title = context.string(node, "title") ?? context.string(node, "placeholder") ?? ""
        let text = JsonBindings.string(node, "text", context, onChange: context.action(node, "onChange"))
        VStack(alignment: .leading, spacing: 4) {
            Group {
                if secure {
                    SecureField(title, text: text)
                } else {
                    TextField(title, text: text)
                }
            }
            .onSubmit { context.perform(node, "onCommit") }
            .modifier(KeyboardModifier(keyboard: node["keyboard"].stringValue, autocapitalization: node["autocapitalization"].stringValue, secure: secure))
            errorText(node, context)
        }
    }

    @ViewBuilder
    static func textEditor(_ node: JsonNode, _ context: JsonContext) -> some View {
        let text = JsonBindings.string(node, "text", context, onChange: context.action(node, "onChange"))
        let minHeight = CGFloat(node["minHeight"].doubleValue ?? 60)
        VStack(alignment: .leading, spacing: 4) {
            #if os(iOS) || os(macOS) || os(visionOS)
            TextEditor(text: text).frame(minHeight: minHeight)
            #else
            TextField(context.string(node, "title") ?? "", text: text)
            #endif
            errorText(node, context)
        }
    }

    @ViewBuilder
    static func errorText(_ node: JsonNode, _ context: JsonContext) -> some View {
        if let error = context.string(node, "error"), !error.isEmpty {
            Text(error).font(.caption).foregroundColor(.red)
        }
    }

    struct KeyboardModifier: ViewModifier {
        let keyboard: String?
        let autocapitalization: String?
        let secure: Bool

        func body(content: Content) -> some View {
            #if os(iOS) || os(tvOS) || os(visionOS)
            content
                .keyboardType(keyboardType)
                .textInputAutocapitalization(capitalization)
                .disableAutocorrection(secure || keyboard == "email" || keyboard == "url" || autocapitalization == "none")
            #else
            content.disableAutocorrection(secure || keyboard == "email" || keyboard == "url")
            #endif
        }

        #if os(iOS) || os(tvOS) || os(visionOS)
        var keyboardType: UIKeyboardType {
            switch keyboard {
            case "email": return .emailAddress
            case "number": return .numberPad
            case "decimal": return .decimalPad
            case "phone": return .phonePad
            case "url": return .URL
            default: return .default
            }
        }

        var capitalization: TextInputAutocapitalization? {
            switch autocapitalization {
            case "none": return .never
            case "words": return .words
            case "sentences": return .sentences
            case "characters": return .characters
            default: return keyboard == "email" || keyboard == "url" ? .never : nil
            }
        }
        #endif
    }

    // MARK: - Toggle

    @ViewBuilder
    static func toggle(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        Toggle(isOn: JsonBindings.bool(node, "isOn", context, onChange: context.action(node, "onChange"))) {
            JsonLabelView(value: node["label"], context: context, model: model)
        }
    }

    // MARK: - Picker

    struct PickerOption: Identifiable {
        let value: JsonValue
        let label: String
        var id: String { value.stringValue ?? value.jsonString() }
    }

    static func options(_ node: JsonNode, _ context: JsonContext) -> [PickerOption] {
        (context.resolve(node, "options").arrayValue ?? []).map { option in
            if case .object(let o) = option {
                let value = o["value"] ?? o["id"] ?? .null
                return PickerOption(value: value, label: o["label"]?.stringValue ?? o["title"]?.stringValue ?? value.stringValue ?? "")
            }
            return PickerOption(value: option, label: option.stringValue ?? "")
        }
    }

    @ViewBuilder
    static func picker(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        let options = options(node, context)
        let onChange = context.action(node, "onChange")
        let path = context.bindingPath(node, "selection")
        let selection = Binding<String>(
            get: {
                let current = path.map { context.get($0) } ?? context.resolve(node, "selection")
                return current.stringValue ?? current.jsonString()
            },
            set: { id in
                guard let path = path, let option = options.first(where: { $0.id == id }) else { return }
                guard context.get(path) != option.value else { return }
                context.set(option.value, at: path)
                if let onChange = onChange { context.perform(onChange) }
            })
        let picker = Picker(selection: selection) {
            ForEach(options) { option in
                Text(option.label).tag(option.id)
            }
        } label: {
            JsonLabelView(value: node["label"], context: context, model: model)
        }
        switch context.string(node, "style") ?? "" {
        case "segmented":
            #if os(watchOS)
            picker
            #else
            picker.pickerStyle(.segmented)
            #endif
        case "wheel":
            #if os(iOS) || os(watchOS)
            picker.pickerStyle(.wheel)
            #else
            picker
            #endif
        case "inline":
            picker.pickerStyle(.inline)
        case "menu":
            #if os(iOS) || os(macOS) || os(visionOS)
            picker.pickerStyle(.menu)
            #else
            picker
            #endif
        default:
            picker
        }
    }

    // MARK: - DatePicker

    static let dateOnly: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .iso8601)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static let timeOnly: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "HH:mm"
        return f
    }()

    static let iso8601: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static func parseDate(_ text: String?, components: String) -> Date? {
        guard let text = text, !text.isEmpty else { return nil }
        switch components {
        case "time": return timeOnly.date(from: text) ?? iso8601.date(from: text)
        case "date": return dateOnly.date(from: text) ?? iso8601.date(from: text)
        default: return iso8601.date(from: text) ?? dateOnly.date(from: text)
        }
    }

    static func formatDate(_ date: Date, components: String) -> String {
        switch components {
        case "time": return timeOnly.string(from: date)
        case "date": return dateOnly.string(from: date)
        default: return iso8601.string(from: date)
        }
    }

    @ViewBuilder
    static func datePicker(_ node: JsonNode, _ context: JsonContext) -> some View {
        #if os(tvOS) || os(watchOS)
        Text(context.string(node, "label") ?? "")
        #else
        let components = context.string(node, "components", default: "date")
        let label = context.string(node, "label") ?? ""
        let onChange = context.action(node, "onChange")
        let path = context.bindingPath(node, "selection")
        let displayed: DatePickerComponents = components == "time" ? [.hourAndMinute] : components == "dateAndTime" ? [.date, .hourAndMinute] : [.date]
        let selection = Binding<Date>(
            get: { parseDate((path.map { context.get($0) } ?? context.resolve(node, "selection")).stringValue, components: components) ?? Date() },
            set: { date in
                guard let path = path else { return }
                let text = formatDate(date, components: components)
                guard context.get(path).stringValue != text else { return }
                context.set(.string(text), at: path)
                if let onChange = onChange { context.perform(onChange) }
            })
        let min = parseDate(context.string(node, "min"), components: components)
        let max = parseDate(context.string(node, "max"), components: components)
        if let min = min, let max = max {
            DatePicker(label, selection: selection, in: min...max, displayedComponents: displayed)
        } else if let min = min {
            DatePicker(label, selection: selection, in: min..., displayedComponents: displayed)
        } else if let max = max {
            DatePicker(label, selection: selection, in: ...max, displayedComponents: displayed)
        } else {
            DatePicker(label, selection: selection, displayedComponents: displayed)
        }
        #endif
    }

    // MARK: - Slider / Stepper

    @ViewBuilder
    static func slider(_ node: JsonNode, _ context: JsonContext) -> some View {
        #if os(tvOS)
        Text(context.string(node, "label") ?? "")
        #else
        let min = context.double(node, "min", default: 0)
        let max = context.double(node, "max", default: 1)
        let value = JsonBindings.double(node, "value", context, default: min, onChange: context.action(node, "onChange"))
        let label = context.string(node, "label")
        HStack {
            if let label = label { Text(label) }
            if let step = node["step"].doubleValue, step > 0 {
                Slider(value: value, in: min...max, step: step)
            } else {
                Slider(value: value, in: min...max)
            }
        }
        #endif
    }

    @ViewBuilder
    static func stepper(_ node: JsonNode, _ context: JsonContext) -> some View {
        #if os(tvOS) || os(watchOS)
        Text(context.string(node, "label") ?? "")
        #else
        let value = JsonBindings.double(node, "value", context, onChange: context.action(node, "onChange"))
        let label = context.string(node, "label") ?? ""
        let step = node["step"].doubleValue ?? 1
        let min = context.double(node, "min") ?? -Double.greatestFiniteMagnitude
        let max = context.double(node, "max") ?? Double.greatestFiniteMagnitude
        Stepper(label, value: value, in: min...max, step: step)
        #endif
    }

    // MARK: - Button

    @ViewBuilder
    static func button(_ node: JsonNode, _ context: JsonContext, _ model: JsonUIModel) -> some View {
        let role: ButtonRole? = node["role"].stringValue == "destructive" ? .destructive : node["role"].stringValue == "cancel" ? .cancel : nil
        let button = Button(role: role) {
            context.perform(node, "action")
        } label: {
            JsonLabelView(value: node["label"], context: context, model: model)
                .frame(maxWidth: node["frame"]["maxWidth"].stringValue == "infinity" ? .infinity : nil)
        }
        switch context.string(node, "style") ?? "" {
        case "borderedProminent":
            #if os(watchOS)
            button.buttonStyle(.bordered)
            #else
            button.buttonStyle(.borderedProminent)
            #endif
        case "bordered": button.buttonStyle(.bordered)
        case "plain": button.buttonStyle(.plain)
        case "borderless":
            #if os(tvOS)
            button.buttonStyle(.plain)
            #else
            button.buttonStyle(.borderless)
            #endif
        default: button
        }
    }
}
#endif
