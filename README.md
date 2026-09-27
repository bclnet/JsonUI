# JsonUI

Render forms and simple screens on iOS and Android from one JSON definition.

| platform | library | UI toolkit | script engine |
| --- | --- | --- | --- |
| iOS, macOS, tvOS, watchOS | Swift package `JsonUI` (targets `JsonUICore`, `JsonUI`, sources in `ios/`) | SwiftUI | JavaScriptCore |
| Android | Gradle modules `jsonui-core`, `jsonui-compose` (in `android/`) | Jetpack Compose (Material 3) | QuickJS |

JsonUI is the successor of [SwiftUIJson](https://github.com/bclnet/SwiftUIJson).
SwiftUIJson reflected a live SwiftUI hierarchy through `Mirror` to produce
JSON and rebuilt it with a type registry; that tied it to private SwiftUI
internals and could not be ported. JsonUI keeps the ideas (a `type`-keyed
node tree, `content` for children, a context with state and actions, a
`JsonPreview` for development) but makes the document the source of truth:
both platforms parse the same JSON, bind inputs to a shared state store and run
the same JavaScript for validation and logic.

The format is documented in [docs/SCHEMA.md](docs/SCHEMA.md) and
[schema/jsonui.schema.json](schema/jsonui.schema.json). Example forms are in
[samples/](samples/).

## A document

```json
{
  "_ui": {
    "state": { "email": "", "password": "", "remember": false },
    "script": "function canSubmit() { return state.email.indexOf('@') > 0 && state.password.length >= 8; }"
  },
  "type": "Form",
  "content": [
    { "type": "Section", "header": "Account", "content": [
      { "type": "TextField", "title": "Email", "text": "$email", "keyboard": "email" },
      { "type": "SecureField", "title": "Password", "text": "$password" },
      { "type": "Toggle", "label": "Remember me", "isOn": "$remember" }
    ]},
    { "type": "Button", "label": "Sign in", "style": "borderedProminent",
      "disabled": "${!canSubmit()}",
      "action": { "name": "login", "args": { "email": "$email", "remember": "$remember" } } }
  ]
}
```

* `"$email"` binds a property to `state.email`; input views write back to it.
* `"${expr}"` evaluates JavaScript with `state` and the functions from `_ui.script` in scope. `"Hello ${state.name}"` interpolates.
* Actions are data: a host action name (`"login"`), a script (`"js: submit()"`), a state assignment (`{"set": {...}}`) or a list of those.
* `ForEach`, `If`, `Picker`, `DatePicker`, `Slider`, `Stepper`, stacks, `Section`, `NavigationView` and modifiers (`padding`, `frame`, `font`, `background`, ...) are listed in the schema document.

## iOS / SwiftUI

Add the package by URL (the `Package.swift` stays at the repository root so
SwiftPM can find it; the sources live in `ios/`) and depend on the `JsonUI`
product.

```swift
import SwiftUI
import JsonUI

struct LoginScreen: View {
    @StateObject private var model: JsonUIModel = {
        let model = try! JsonUIModel(json: loginJson)          // JavaScriptCore engine by default
        model.on("login") { args in
            print("login", args["email"].stringValue ?? "")
        }
        return model
    }()

    var body: some View {
        JsonUIView(model: model)
    }
}
```

* `JsonUIModel` owns the state (`model.store`), the script engine and the host actions (`model.on(name) { ... }`). It is an `ObservableObject`; the view re-renders on every state change, including changes made by scripts.
* Custom node types: `JsonViewRegistry.shared.register("Signature") { node, context in SignatureView(...) }`.
* `JsonPreview(json: ...)` shows the rendered form next to its live state and JSON in an Xcode preview.
* `JsonUICore` (Foundation only, builds on Linux) contains the model, `JsonStore`, `JsonContext`, `NoScriptEngine` and a builder DSL:

```swift
let doc = JsonDocument(header: JsonUIHeader(state: ["email": ""])) {
    JsonNode.form {
        JsonNode.section("Account") {
            JsonNode.textField("Email", text: "$email").keyboard("email")
            JsonNode.button("Sign in", host: "login", args: ["email": "$email"])
        }
    }
}
let json = doc.jsonString()
```

## Android / Jetpack Compose

The Gradle project lives in `android/`. Publish the modules with
`./gradlew publishToMavenLocal` (group `com.bclnet.jsonui`, artifacts
`jsonui-core` and `jsonui-compose`) or include them as a composite build.

```kotlin
import com.bclnet.jsonui.JsonDocument
import com.bclnet.jsonui.compose.JsonUIView
import com.bclnet.jsonui.compose.rememberJsonUIModel

@Composable
fun LoginScreen(json: String) {
    val document = remember(json) { JsonDocument.parse(json) }
    val model = rememberJsonUIModel(document)                    // QuickJS engine, closed on dispose
    LaunchedEffect(model) {
        model.on("login") { args -> Log.i("Login", args.toString()) }
    }
    JsonUIView(model)
}
```

* `JsonUIModel` mirrors the Swift model: `model.store`, `model.actions`, `model.on(name) { ... }`, `model.version` (a Compose state that recomposes readers).
* Custom node types: `JsonViewRegistry.shared.register("Signature") { node, context, model -> SignatureView(...) }`.
* `JsonPreview(json)` renders the form above its state and JSON for `@Preview` composables.
* `jsonui-core` is a plain Kotlin/JVM module with the model, `JsonStore`, `JsonContext`, `NoScriptEngine` and the DSL:

```kotlin
val doc = jsonDocument(state = mapOf("email" to "")) {
    form {
        section("Account") {
            textField("Email", text = "\$email").keyboard("email")
            button("Sign in", host = "login", args = mapOf("email" to "\$email"))
        }
    }
}
val json = doc.toJsonString()
```

## Script runtime

Both engines install the same prelude (`scripts/jsonui-prelude.js`), which
exposes:

* `state` – live proxy over the form state (`state.email = 'x'` updates the UI)
* `setState({...})`, `getState()`
* `host.invoke(name, args)` – call a host action and get its result
* `console.log(...)`

Only JSON strings cross the native bridge, so the prelude behaves identically
on JavaScriptCore and QuickJS. `scripts/test-prelude.js` exercises it under
Node; `scripts/sync-prelude.py` re-embeds it into both libraries after edits.

## Repository layout

```
Package.swift                     Swift package manifest (must be at the root for SwiftPM URLs)
ios/                              Swift sources and tests (JsonUICore, JsonUI)
android/                          Gradle project (jsonui-core, jsonui-compose)
docs/SCHEMA.md                    format reference
schema/jsonui.schema.json         JSON Schema for editors and validation
samples/                          example documents used by both test suites
scripts/                          shared JS prelude, its Node test and the sync script
```

## Building and testing

```
swift build && swift test                 # core tests run on Linux and macOS; SwiftUI target needs Xcode
cd android && ./gradlew build             # JVM tests for jsonui-core, AAR for jsonui-compose
cd android && ./gradlew connectedCheck    # QuickJS engine test on a device/emulator
node scripts/test-prelude.js              # shared script runtime
```

## License

MIT, see [LICENSE](LICENSE).
