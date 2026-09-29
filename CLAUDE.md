# JsonUI

Declarative UI from JSON for iOS (SwiftUI) and Android (Compose), with a shared
JavaScript prelude for expressions and actions. This is the base library of a
family of repositories by bclnet; the others depend on it by URL:

| repo | role | depends on |
| --- | --- | --- |
| JsonUI (this) | JSON documents → native views, fragments, scripting | – |
| JsonMind | minds for scene actors: prompts, command vocabulary, sessions, providers | JsonUI, TokenX (adapter only) |
| JsonScene | the `Scene` node: 3D actors, bodies, mobility, behaviors; SceneKit / Filament / Meta Spatial renderers | JsonUI, JsonMind |
| TokenX | AI token management: providers, keys, usage, server/client in one process, embeddable settings UI | – |
| QRX | the app: scans QR "glyphs" and places their JsonUI content in AR (iOS, Android, Quest) | all of the above |

`docs/SCHEMA.md` is the document format reference, `schema/jsonui.schema.json`
the JSON Schema, `samples/` the example documents.

## Layout

```
Package.swift                 root manifest so SwiftPM can add the package by URL
ios/Sources/JsonUICore        Foundation only: JsonValue, JsonDocument, JsonNode, JsonContext, JsonStore,
                              DynamicValue, JsonPath, JsonFragments ($ref), ScriptEngine, ScriptPrelude
ios/Sources/JsonUI            SwiftUI: JsonNodeView, JsonViewRegistry (custom nodes), JsonPreview, JavaScriptCoreEngine
ios/Tests                     JsonUICoreTests run on Linux; JsonUITests need Xcode
android/jsonui-core           Kotlin/JVM mirror of JsonUICore (JsonFragments.kt, JsonValues.kt, ...)
android/jsonui-compose        Compose renderer + QuickJS engine (minSdk 26)
scripts/jsonui-prelude.js     the shared JS prelude; sync-prelude.py copies it into both platforms, test-prelude.js tests it
```

## Build and test

```
swift build && swift test                 # Linux or macOS: core tests (SwiftUI target compiles only in Xcode)
cd android && ./gradlew build             # JVM tests for jsonui-core, AAR for jsonui-compose
node scripts/test-prelude.js              # prelude tests
python3 scripts/sync-prelude.py           # after editing the prelude; CI fails if the copies drift
```

CI (`.github/workflows`) runs all of the above plus an iOS Simulator build.

## Conventions

- Keep Swift and Kotlin in step: a feature lands in JsonUICore and jsonui-core together,
  with the same names where the languages allow, and a test on each side.
- Strict accessors on `JsonValue` (`text`, `numberValue`, `integerValue`, `flag`) return nil
  unless the value has that type. `stringValue` stringifies anything and is for display only;
  parsing code must use the strict ones.
- Fragments: `{ "$ref": "url#/json/pointer" }`. `#name` means `/_ui/fragments/name` of the
  current document, relative URLs resolve against the document URL, keys beside `$ref`
  override the fragment shallowly (`null` removes a key), an array fragment is spliced into
  `content`, nested refs resolve against the fragment's own document, cycles are an error.
  Swift: `JsonFragmentResolver` and `JsonDocument(value:base:resolver:)`. Kotlin: `JsonFragments`
  and `JsonDocument.fromValue(value, base, fragments)`. Hosts supply the loader.
- Custom nodes register through `JsonViewRegistry` (Swift) / the Compose registry; JsonScene's
  `Scene` node is the reference example of an external node.
- Documentation lives in `docs/SCHEMA.md` and the README; update them with the code.

## Gotchas

- The Swift 6 toolchain on Linux compiles only Foundation targets; anything under
  `#if canImport(SwiftUI)` is not checked there. Parse-check with `swiftc -parse` at least.
- jsonui-compose needs minSdk 26 for java.time; lower it and lint fails.
- Commit messages must not name AI models; the required trailers are added by the session.
