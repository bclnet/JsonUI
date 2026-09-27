//
//  JsonPreview.swift
//  JsonUI
//
//  Development aid that shows the rendered form next to its JSON and live
//  state, the counterpart of SwiftUIJson's `JsonPreview`.
//
//      struct LoginForm_Previews: PreviewProvider {
//          static var previews: some View {
//              JsonPreview(json: loginJson)
//          }
//      }
//

#if canImport(SwiftUI) && canImport(Combine)
import SwiftUI
import JsonUICore

public struct JsonPreview: View {
    @StateObject private var model: JsonUIModel
    private let error: String?

    public init(model: JsonUIModel) {
        self.init(model: model, error: nil)
    }

    private init(model: JsonUIModel, error: String?) {
        _model = StateObject(wrappedValue: model)
        self.error = error
    }

    public init(document: JsonDocument, actions: JsonActions = JsonActions()) {
        self.init(model: JsonUIModel(document: document, actions: actions))
    }

    public init(json: String, actions: JsonActions = JsonActions()) {
        do {
            self.init(model: JsonUIModel(document: try JsonDocument(json: json), actions: actions), error: nil)
        } catch {
            self.init(model: JsonUIModel(document: JsonDocument(root: .text("")), engine: NoScriptEngine()), error: "\(error)")
        }
    }

    public init(actions: JsonActions = JsonActions(), header: JsonUIHeader = JsonUIHeader(), @JsonNodeBuilder content: () -> [JsonNode]) {
        self.init(document: JsonDocument(header: header, content: content), actions: actions)
    }

    public var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 8) {
                if let error = error {
                    Text(error).foregroundColor(.red).padding()
                } else {
                    JsonUIView(model: model)
                        .frame(height: geometry.size.height * 0.6)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray, lineWidth: 1))
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("State").font(.headline)
                        Text(model.state.jsonString(pretty: true)).font(.system(.caption, design: .monospaced))
                        if !model.runtime.scriptErrors.isEmpty {
                            Text("Script errors").font(.headline).foregroundColor(.red)
                            ForEach(Array(model.runtime.scriptErrors.enumerated()), id: \.offset) { _, message in
                                Text(message).font(.caption).foregroundColor(.red)
                            }
                        }
                        Text("Document").font(.headline)
                        Text(model.document.jsonString()).font(.system(.caption, design: .monospaced))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                }
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray, lineWidth: 1))
            }
            .padding()
        }
    }
}
#endif
