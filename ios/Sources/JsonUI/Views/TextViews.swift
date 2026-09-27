//
//  TextViews.swift
//  JsonUI
//
//  Text, Label, Image, Link and ProgressView.
//

#if canImport(SwiftUI)
import SwiftUI
import JsonUICore

enum JsonTextViews {
    @ViewBuilder
    static func text(_ node: JsonNode, _ context: JsonContext) -> some View {
        let text = context.string(node, "text") ?? ""
        Text(text)
            .lineLimit(node["lineLimit"].intValue)
            .multilineTextAlignment(JsonStyles.textAlignment(node["multilineTextAlignment"].stringValue))
    }

    @ViewBuilder
    static func label(_ node: JsonNode, _ context: JsonContext) -> some View {
        let title = context.string(node, "title") ?? context.string(node, "text") ?? ""
        if let systemImage = node["systemImage"].stringValue {
            Label(title, systemImage: systemImage)
        } else {
            Text(title)
        }
    }

    @ViewBuilder
    static func image(_ node: JsonNode, _ context: JsonContext) -> some View {
        let resizable = context.bool(node, "resizable", default: node.has("width") || node.has("height"))
        let fill = context.string(node, "contentMode") == "fill"
        let width = node["width"].doubleValue.map { CGFloat($0) }
        let height = node["height"].doubleValue.map { CGFloat($0) }
        if let systemName = context.string(node, "systemName") {
            sized(Image(systemName: systemName), resizable: resizable, fill: fill, width: width, height: height)
        } else if let name = context.string(node, "name") {
            sized(Image(name), resizable: resizable, fill: fill, width: width, height: height)
        } else if let urlString = context.string(node, "url"), let url = URL(string: urlString) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image): sized(image, resizable: true, fill: fill, width: width, height: height)
                case .failure: Image(systemName: "photo").foregroundColor(.secondary)
                default: ProgressView()
                }
            }
            .frame(width: width, height: height)
        } else {
            Image(systemName: "questionmark.square.dashed")
        }
    }

    @ViewBuilder
    private static func sized(_ image: Image, resizable: Bool, fill: Bool, width: CGFloat?, height: CGFloat?) -> some View {
        if resizable {
            image.resizable()
                .aspectRatio(contentMode: fill ? .fill : .fit)
                .frame(width: width, height: height)
                .clipped()
        } else {
            image
        }
    }

    @ViewBuilder
    static func link(_ node: JsonNode, _ context: JsonContext) -> some View {
        let title = context.string(node, "title") ?? context.string(node, "text") ?? ""
        if let urlString = context.string(node, "url"), let url = URL(string: urlString) {
            Link(title, destination: url)
        } else {
            Text(title)
        }
    }

    @ViewBuilder
    static func progress(_ node: JsonNode, _ context: JsonContext) -> some View {
        let label = context.string(node, "label")
        if let value = context.double(node, "value") {
            let total = context.double(node, "total", default: 1)
            if let label = label {
                ProgressView(label, value: value, total: total)
            } else {
                ProgressView(value: value, total: total)
            }
        } else if let label = label {
            ProgressView(label)
        } else {
            ProgressView()
        }
    }
}
#endif
