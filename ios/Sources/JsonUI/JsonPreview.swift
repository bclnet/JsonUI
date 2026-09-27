//
//  JsonPreview.swift
//  JsonUI
//
//  Development aid, the counterpart of SwiftUIJson's `JsonPreview`. Wrap an
//  existing SwiftUI view to see it side by side with the JsonUI rendering of
//  the document reflected from it, plus the document itself:
//
//      struct LoginForm_Previews: PreviewProvider {
//          static var previews: some View {
//              JsonPreview {
//                  LoginForm()
//              }
//          }
//      }
//
//  `JsonPreview(json:)`, `JsonPreview(document:)` and the builder form show a
//  document without an original view.
//

#if canImport(SwiftUI) && canImport(Combine)
import SwiftUI
import JsonUICore

public struct JsonPreview: View {
    @StateObject private var model: JsonUIModel
    private let original: AnyView?
    private let warnings: [String]
    private let error: String?

    /// Reflects `content` into a document (see `JsonReflector`) and shows the
    /// original next to the JsonUI rendering. Button actions and other
    /// closures of the original view are wired to the rendered copy.
    public init<Content: View>(actions: JsonActions = JsonActions(), @ViewBuilder content: () -> Content) {
        let view = content()
        let reflector = JsonReflector(actions: actions)
        let document = reflector.reflect(view)
        self.init(model: JsonUIModel(document: document, actions: reflector.actions), original: AnyView(view), warnings: reflector.warnings, error: nil)
    }

    public init(model: JsonUIModel) {
        self.init(model: model, original: nil, warnings: [], error: nil)
    }

    public init(document: JsonDocument, actions: JsonActions = JsonActions()) {
        self.init(model: JsonUIModel(document: document, actions: actions))
    }

    public init(json: String, actions: JsonActions = JsonActions()) {
        do {
            self.init(model: JsonUIModel(document: try JsonDocument(json: json), actions: actions), original: nil, warnings: [], error: nil)
        } catch {
            self.init(model: JsonUIModel(document: JsonDocument(root: .text("")), engine: NoScriptEngine()), original: nil, warnings: [], error: "\(error)")
        }
    }

    public init(actions: JsonActions = JsonActions(), header: JsonUIHeader = JsonUIHeader(), @JsonNodeBuilder nodes: () -> [JsonNode]) {
        self.init(document: JsonDocument(header: header, content: nodes), actions: actions)
    }

    private init(model: JsonUIModel, original: AnyView?, warnings: [String], error: String?) {
        _model = StateObject(wrappedValue: model)
        self.original = original
        self.warnings = warnings
        self.error = error
    }

    public var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 8) {
                if let error = error {
                    Text(error).foregroundColor(.red).padding()
                } else {
                    HStack(spacing: 8) {
                        if let original = original {
                            original
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray, lineWidth: 1))
                        }
                        JsonUIView(model: model)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.accentColor, lineWidth: 1))
                    }
                    .frame(height: geometry.size.height * 0.6)
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        if !warnings.isEmpty {
                            Text("Reflection warnings").font(.headline).foregroundColor(.orange)
                            ForEach(Array(warnings.enumerated()), id: \.offset) { _, message in
                                Text(message).font(.caption).foregroundColor(.orange)
                            }
                        }
                        Text("State").font(.headline)
                        Text(model.state.jsonString(pretty: true)).font(.system(.caption, design: .monospaced))
                        if !model.runtime.scriptErrors.isEmpty {
                            Text("Script errors").font(.headline).foregroundColor(.red)
                            ForEach(Array(model.runtime.scriptErrors.enumerated()), id: \.offset) { _, message in
                                Text(message).font(.caption).foregroundColor(.red)
                            }
                        }
                        HStack {
                            Text("Document").font(.headline)
                            Spacer()
                            Button {
                                #if os(macOS)
                                NSPasteboard.general.clearContents()
                                NSPasteboard.general.setString(model.document.jsonString(), forType: .string)
                                #elseif canImport(UIKit) && !os(watchOS) && !os(tvOS)
                                UIPasteboard.general.string = model.document.jsonString()
                                #endif
                            } label: {
                                Image(systemName: "doc.on.doc")
                            }
                            .accessibilityLabel(Text("Copy document"))
                        }
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
