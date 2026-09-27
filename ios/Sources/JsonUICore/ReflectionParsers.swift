//
//  ReflectionParsers.swift
//  JsonUI
//
//  Pure parsers used by the SwiftUI view reflector (ios/Sources/JsonUI/JsonReflector.swift).
//  SwiftUI's `Font`, `Color` and `Edge.Set` do not expose their configuration,
//  but their `description` / raw values do, and these helpers turn those into
//  JsonUI style values. They live in the core so they can be tested on Linux.
//

import Foundation

public enum JsonReflectionParsers {
    // MARK: - Fonts

    public struct FontDescription: Equatable {
        public var style: String?
        public var size: Double?
        public var weight: String?
        public var design: String?
        public var name: String?
        public var bold = false
        public var italic = false

        public init() {}

        /// The `font` property value, or nil when nothing was recognised.
        public var value: JsonValue? {
            if let name = name {
                var o: [String: JsonValue] = ["name": .string(name)]
                if let size = size { o["size"] = .number(size) }
                return .object(o)
            }
            if let style = style, size == nil, weight == nil, design == nil { return .string(style) }
            if size == nil && weight == nil && design == nil { return nil }
            var o: [String: JsonValue] = [:]
            if let size = size { o["size"] = .number(size) }
            if let weight = weight { o["weight"] = .string(weight) }
            if let design = design, design != "default" { o["design"] = .string(design) }
            if let style = style { o["style"] = .string(style) }
            return .object(o)
        }
    }

    static let textStyles = ["largeTitle", "title2", "title3", "title", "headline", "subheadline", "body", "callout", "footnote", "caption2", "caption"]
    static let designs = ["monospaced", "rounded", "serif", "default"]

    /// Parses `String(describing: font)`, e.g.
    /// `Font(provider: SwiftUI.(unknown context at $1d8).TextStyleProvider(textStyle: SwiftUI.Font.TextStyle.headline, design: SwiftUI.Font.Design.default, weight: nil))`.
    public static func font(fromDescription text: String) -> FontDescription {
        var font = FontDescription()
        if text.contains("BoldModifier") { font.bold = true }
        if text.contains("ItalicModifier") { font.italic = true }
        if let style = firstMatch(#"TextStyle\.([A-Za-z0-9]+)"#, in: text) { font.style = style }
        if let size = firstMatch(#"size:\s*([0-9]+(?:\.[0-9]+)?)"#, in: text), let n = Double(size) { font.size = n }
        if let design = firstMatch(#"Design\.([A-Za-z]+)"#, in: text), design != "default" { font.design = design }
        if let name = firstMatch(#"name:\s*"([^"]*)""#, in: text) { font.name = name }
        if let weightValue = firstMatch(#"Weight\(value:\s*(-?[0-9]+(?:\.[0-9]+)?)\)"#, in: text), let n = Double(weightValue) {
            font.weight = weightName(forValue: n)
        }
        if font.weight == "bold" { font.bold = true; font.weight = nil }
        return font
    }

    /// SwiftUI encodes `Font.Weight` as a number between -1 and 1.
    public static func weightName(forValue value: Double) -> String? {
        let table: [(String, Double)] = [("ultraLight", -0.8), ("thin", -0.6), ("light", -0.4), ("regular", 0), ("medium", 0.23), ("semibold", 0.3), ("bold", 0.4), ("heavy", 0.56), ("black", 0.62)]
        guard let closest = table.min(by: { abs($0.1 - value) < abs($1.1 - value) }) else { return nil }
        return closest.0 == "regular" ? nil : closest.0
    }

    // MARK: - Colors

    static let namedColors: Set<String> = ["primary", "secondary", "accent", "clear", "black", "white", "gray", "red", "green", "blue", "orange", "yellow", "pink", "purple", "mint", "teal", "cyan", "indigo", "brown"]

    /// Parses `String(describing: color)`: `red`, `#FF0000FF` (RRGGBBAA), `NamedColor(name: "brand", bundle: nil)`.
    public static func color(fromDescription text: String) -> JsonValue? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("#") {
            let hex = String(trimmed.dropFirst()).uppercased()
            if hex.count == 8 {
                // SwiftUI prints RRGGBBAA; JsonUI uses AARRGGBB.
                let rgb = hex.prefix(6), alpha = hex.suffix(2)
                return .string(alpha == "FF" ? "#\(rgb)" : "#\(alpha)\(rgb)")
            }
            if hex.count == 6 { return .string("#\(hex)") }
            return nil
        }
        let lower = trimmed.lowercased()
        if lower == "accentcolor" { return .string("accent") }
        if namedColors.contains(lower) { return .string(lower) }
        if let name = firstMatch(#"NamedColor\(name:\s*"([^"]*)""#, in: trimmed) { return .string(name) }
        if let name = firstMatch(#"name:\s*"([^"]*)""#, in: trimmed) { return .string(name) }
        return nil
    }

    // MARK: - Edges

    /// Names an `Edge.Set` raw value (top 1, leading 2, bottom 4, trailing 8).
    public static func edges(fromRawValue raw: Int) -> String? {
        switch raw & 15 {
        case 15: return "all"
        case 10: return "horizontal"
        case 5: return "vertical"
        case 1: return "top"
        case 2: return "leading"
        case 4: return "bottom"
        case 8: return "trailing"
        default: return nil
        }
    }

    // MARK: - Localized string keys

    /// Substitutes `%@`, `%lld`, `%.2f` ... placeholders of a `LocalizedStringKey` with argument strings.
    public static func format(key: String, arguments: [String]) -> String {
        guard !arguments.isEmpty, let regex = try? NSRegularExpression(pattern: #"%[-+ 0-9.]*(?:ll|l|h|q|z)?[@dfisuxXeEgGc]"#) else { return key }
        var result = key
        var index = 0
        while index < arguments.count, let match = regex.firstMatch(in: result, range: NSRange(result.startIndex..., in: result)), let range = Range(match.range, in: result) {
            result.replaceSubrange(range, with: arguments[index])
            index += 1
        }
        return result.replacingOccurrences(of: "%%", with: "%")
    }

    // MARK: - Helpers

    static func firstMatch(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              match.numberOfRanges > 1, let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range])
    }
}
