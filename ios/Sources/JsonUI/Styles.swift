//
//  Styles.swift
//  JsonUI
//
//  Parsing of colors, fonts, alignments and other style values.
//

#if canImport(SwiftUI)
import SwiftUI
import JsonUICore

enum JsonStyles {
    // MARK: - Colors

    static func color(_ value: JsonValue, context: JsonContext) -> Color? {
        let resolved = context.resolve(value)
        if case .object(let o) = resolved {
            let scheme: JsonValue
            #if canImport(UIKit) && !os(watchOS)
            scheme = UITraitCollection.current.userInterfaceStyle == .dark ? (o["dark"] ?? o["light"] ?? .null) : (o["light"] ?? o["dark"] ?? .null)
            #else
            scheme = o["light"] ?? o["dark"] ?? .null
            #endif
            return color(scheme, context: context)
        }
        guard let text = resolved.stringValue?.trimmingCharacters(in: .whitespaces), !text.isEmpty else { return nil }
        if text.hasPrefix("#") { return hexColor(text) }
        switch text.lowercased() {
        case "primary": return .primary
        case "secondary": return .secondary
        case "accent", "accentcolor": return .accentColor
        case "clear", "transparent": return .clear
        case "black": return .black
        case "white": return .white
        case "gray", "grey": return .gray
        case "red": return .red
        case "green": return .green
        case "blue": return .blue
        case "orange": return .orange
        case "yellow": return .yellow
        case "pink": return .pink
        case "purple": return .purple
        case "mint": return .mint
        case "teal": return .teal
        case "cyan": return .cyan
        case "indigo": return .indigo
        case "brown": return .brown
        default: return nil
        }
    }

    static func hexColor(_ text: String) -> Color? {
        var hex = String(text.dropFirst())
        if hex.count == 3 || hex.count == 4 { hex = hex.map { "\($0)\($0)" }.joined() }
        guard hex.count == 6 || hex.count == 8, let value = UInt64(hex, radix: 16) else { return nil }
        let a, r, g, b: Double
        if hex.count == 8 {
            a = Double((value >> 24) & 0xff) / 255
            r = Double((value >> 16) & 0xff) / 255
            g = Double((value >> 8) & 0xff) / 255
            b = Double(value & 0xff) / 255
        } else {
            a = 1
            r = Double((value >> 16) & 0xff) / 255
            g = Double((value >> 8) & 0xff) / 255
            b = Double(value & 0xff) / 255
        }
        return Color(red: r, green: g, blue: b, opacity: a)
    }

    // MARK: - Fonts

    static func font(_ value: JsonValue, context: JsonContext) -> Font? {
        let resolved = context.resolve(value)
        if case .object(let o) = resolved {
            let size = o["size"]?.doubleValue ?? 17
            let weight = fontWeight(o["weight"]?.stringValue) ?? .regular
            let design = fontDesign(o["design"]?.stringValue) ?? .default
            return Font.system(size: CGFloat(size), weight: weight, design: design)
        }
        if let size = resolved.doubleValue { return Font.system(size: CGFloat(size)) }
        guard let name = resolved.stringValue else { return nil }
        switch name {
        case "largeTitle": return .largeTitle
        case "title": return .title
        case "title2": return .title2
        case "title3": return .title3
        case "headline": return .headline
        case "subheadline": return .subheadline
        case "body": return .body
        case "callout": return .callout
        case "footnote": return .footnote
        case "caption": return .caption
        case "caption2": return .caption2
        default: return Font.custom(name, size: 17)
        }
    }

    static func fontWeight(_ name: String?) -> Font.Weight? {
        switch name {
        case "ultraLight": return .ultraLight
        case "thin": return .thin
        case "light": return .light
        case "regular": return .regular
        case "medium": return .medium
        case "semibold": return .semibold
        case "bold": return .bold
        case "heavy": return .heavy
        case "black": return .black
        default: return nil
        }
    }

    static func fontDesign(_ name: String?) -> Font.Design? {
        switch name {
        case "monospaced": return .monospaced
        case "rounded": return .rounded
        case "serif": return .serif
        case "default": return .default
        default: return nil
        }
    }

    // MARK: - Alignment

    static func horizontalAlignment(_ name: String?) -> HorizontalAlignment {
        switch name {
        case "leading": return .leading
        case "trailing": return .trailing
        default: return .center
        }
    }

    static func verticalAlignment(_ name: String?) -> VerticalAlignment {
        switch name {
        case "top": return .top
        case "bottom": return .bottom
        case "firstTextBaseline": return .firstTextBaseline
        case "lastTextBaseline": return .lastTextBaseline
        default: return .center
        }
    }

    static func alignment(_ name: String?) -> Alignment {
        switch name {
        case "leading": return .leading
        case "trailing": return .trailing
        case "top": return .top
        case "bottom": return .bottom
        case "topLeading": return .topLeading
        case "topTrailing": return .topTrailing
        case "bottomLeading": return .bottomLeading
        case "bottomTrailing": return .bottomTrailing
        default: return .center
        }
    }

    static func textAlignment(_ name: String?) -> TextAlignment {
        switch name {
        case "leading": return .leading
        case "trailing": return .trailing
        default: return .center
        }
    }

    static func edges(_ name: String?) -> Edge.Set {
        switch name {
        case "horizontal": return .horizontal
        case "vertical": return .vertical
        case "top": return .top
        case "bottom": return .bottom
        case "leading": return .leading
        case "trailing": return .trailing
        default: return .all
        }
    }

    /// `"infinity"` → `.infinity`, numbers → CGFloat, anything else → nil.
    static func dimension(_ value: JsonValue?) -> CGFloat? {
        guard let value = value else { return nil }
        if let s = value.stringValue?.lowercased(), s == "infinity" || s == "max" { return .infinity }
        return value.doubleValue.map { CGFloat($0) }
    }
}
#endif
