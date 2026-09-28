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

## Related libraries

| library | adds |
| --- | --- |
| [JsonScene](https://github.com/bclnet/JsonScene) | the `Scene` node: animated 3D actors with bodies, mobility and behaviours, rendered with SceneKit, Filament and the Meta Spatial SDK |
| [JsonMind](https://github.com/bclnet/JsonMind) | minds for documents: personas, senses, token budgets and the command vocabulary; a streaming `MindProvider` is the seam for TokenX |
| [QRX](https://github.com/bclnet/QRX) | the apps: QR codes that place forms and scenes in the room on iOS, Android and Quest |

Documents in all of them share JsonUI's state, actions, scripts and
[fragments](docs/SCHEMA.md#fragments).

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
* `JsonPreview { LoginForm() }` reflects an existing SwiftUI view into a document and shows the original, the JsonUI rendering and the JSON side by side (see "Getting JSON from an existing view" below). `JsonPreview(json:)` shows a document on its own.
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
* `JsonPreview { LoginForm() }` reflects an existing composable into a document and shows it next to the JsonUI rendering and the JSON; `JsonPreview(json)` shows a document on its own.
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

## Getting JSON from an existing view

This is the workflow SwiftUIJson's `JsonPreview` provided: build the screen
natively, wrap it, copy the JSON.

**SwiftUI.** `JsonReflector` walks the live view with `Mirror` (the same
internal field names SwiftUIJson used) and emits the document:

```swift
struct LoginForm: View {
    @State private var email = ""
    @State private var remember = false
    var body: some View {
        Form {
            TextField("Email", text: $email).jsonKey("email")
            Toggle("Remember me", isOn: $remember)
            Button("Sign in") { signIn() }.jsonAction("signIn")
        }
    }
}

struct LoginForm_Previews: PreviewProvider {
    static var previews: some View { JsonPreview { LoginForm() } }
}

let document = JsonDocument(reflecting: LoginForm())   // or JsonReflector().reflect(view)
```

* Bindings become state keys. The key comes from `.jsonKey("email")`, else from the `@State` property the binding was made from when that can be inferred (same type and current value), else `text1`, `isOn2`, ...
* Closures cannot be serialized, so button actions, `onAppear` and stepper closures are registered as host actions on the reflector's `JsonActions` (`.jsonAction("signIn")` names them, otherwise `action1`, ...). The preview's rendered copy calls the original closures.
* Supported: `Text` (verbatim, localized, interpolated), `TextField`, `SecureField`, `TextEditor`, `Toggle`, `Button` (with role and button style), `Picker` (tags become option values), `DatePicker`, `Slider`, `Stepper` (as +/− buttons), `ProgressView`, `Image`, `Label`, `Link`, `Form`, `List`, `Section`, stacks, `ScrollView`, `NavigationView`/`NavigationStack`, `Group`, `Spacer`, `Divider`, `if`/`else`, optionals, `AnyView`, custom views (their `body` is reflected) and the modifiers `padding`, `frame`, `background` (colors), `foregroundColor`, `font`, `bold`, `italic`, `opacity`, `cornerRadius`, `disabled`, `lineLimit`, `multilineTextAlignment`, `onAppear`, `hidden`. `ForEach` rows cannot be evaluated and produce a placeholder template.
* Anything else becomes an `Unsupported` node plus an entry in `JsonReflector.warnings`, shown in the preview. SwiftUI's internals are private, so a SwiftUI release can rename a field; the reflector then degrades to warnings rather than crashing.

**Compose.** A composable is a function, not a value tree, so there is nothing
to mirror. The Android reflector reads the composable's **semantics tree**
instead (the public `RootForTest` / `SemanticsOwner` API used by accessibility
and UI tests) and maps it: text, text fields (`EditableText`, password), switches
and checkboxes, radio groups (→ `Picker`), buttons, sliders, progress bars,
icons, disabled state, and layout direction inferred from geometry
(`VStack`/`HStack`/`ZStack`).

```kotlin
@Composable
fun LoginForm() {
    Column {
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") },
            modifier = Modifier.jsonKey("email"))
        Switch(checked = remember, onCheckedChange = { remember = it })
        Button(onClick = { signIn() }, modifier = Modifier.jsonAction("signIn")) { Text("Sign in") }
    }
}

@Preview
@Composable
fun LoginPreview() = JsonPreview { LoginForm() }
```

`Modifier.jsonKey` (or a `testTag`) names the state key of an input and
`Modifier.jsonAction` names the host action of a clickable. The semantics
`OnClick`, `SetText` and `SetProgress` actions of the original composable are
registered as host actions, so the rendered copy drives the original. Fonts,
colors and paddings are not part of semantics and are not reflected; adjust
them in the JSON.

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
