# JsonUI form definition

JsonUI renders a form (or any simple screen) from a JSON document. The same
document is rendered by the Swift package (SwiftUI) and by the Android library
(Jetpack Compose). Dynamic behaviour is provided by an embedded JavaScript
engine: JavaScriptCore on Apple platforms and QuickJS on Android.

The format is a refactoring of the original SwiftUIJson output: nodes carry a
`type` key, containers use `content`, view modifiers are expressed as
properties, and the leading `_ui` header holds the document context (initial
state, script). The old `":Text"` spelling of type names (SwiftUI namespace
prefix) is accepted and treated the same as `"Text"`.

## Document

```json
{
  "_ui": {
    "version": 1,
    "state": { "email": "", "password": "", "remember": false },
    "script": "function isValid() { return state.email.indexOf('@') > 0; }"
  },
  "type": "Form",
  "content": [ ... ]
}
```

| key | description |
| --- | --- |
| `_ui.version` | format version, currently `1` |
| `_ui.state` | initial values for the form state. Keys are binding names. |
| `_ui.script` | JavaScript loaded once into the script engine before rendering. Functions defined here can be used by expressions and actions. |
| `_ui.strings` | optional dictionary of localizable strings. `"@key"` in any string property looks up `key` here. |

Everything else on the root object is the root **node**.

A document may also be a bare node (no `_ui`), in which case state starts
empty.

## Nodes

A node is an object with a `type` and type-specific properties.

```json
{ "type": "TextField", "title": "Email", "text": "$email" }
```

`content` holds the children of a container. It may be a single node or an
array of nodes.

Every node may also have:

| key | type | description |
| --- | --- | --- |
| `id` | string | identity used for `ForEach` keys and for host lookups |
| `hidden` | bool / expression | when true the node is not rendered |
| `disabled` | bool / expression | disables interaction for the node and its children |
| `modifiers` | array | ordered list of modifier objects (see Modifiers) |
| shorthand modifier props | see Modifiers | `padding`, `frame`, `font`, ... |

## Dynamic values

Any property value may be a literal, a **binding** or an **expression**.

| form | meaning |
| --- | --- |
| `"hello"`, `42`, `true` | literal |
| `"$email"` | binding to `state.email` (read for display, written by input views) |
| `{ "$bind": "email" }` | same as `"$email"` |
| `"${ expr }"` | JavaScript expression evaluated by the script engine; the whole string is the expression when it is exactly `${...}` |
| `"Hello ${state.name}!"` | template string: every `${...}` is evaluated and interpolated |
| `{ "$expr": "expr" }` | expression, object form |
| `"@key"` | localized string from `_ui.strings` |
| `"$$literal"`, `"@@literal"` | escaped: renders as `$literal`, `@literal` |

Binding paths may be dotted (`"$address.city"`), which reads and writes
nested objects in the state.

Expressions see a global `state` object (live view of the form state), the
functions from `_ui.script`, and the JsonUI runtime described below.

When no script engine is installed (e.g. in unit tests) only bindings and
templates of the form `${state.path}` resolve; other expressions evaluate to
`null`.

## Actions

Action-valued properties (`action`, `onChange`, `onCommit`, `onAppear`, ...)
accept:

| form | meaning |
| --- | --- |
| `"submit"` | invoke the host action named `submit` |
| `"js: doSomething()"` | run the JavaScript statement(s) in the script engine |
| `{ "script": "..." }` | run JavaScript |
| `{ "name": "submit", "args": { ... } }` | invoke a host action with arguments (arguments are dynamic values) |
| `{ "set": { "step": 2, "done": true } }` | assign state values (values are dynamic values) |
| `[ action, action, ... ]` | run several actions in order |

Host actions are registered by the embedding application. They receive the
action name, the resolved arguments and a handle to the form state.

## Script runtime

The script engine exposes the following globals to `_ui.script`, to
expressions and to script actions:

| global | description |
| --- | --- |
| `state` | live proxy over the form state. Reading `state.x` returns the current value; assigning `state.x = v` updates the store and re-renders. |
| `setState(object)` | merges an object into the state |
| `getState()` | snapshot of the whole state as a plain object |
| `host.invoke(name, args)` | call a host action from script; returns whatever the host returns (JSON-serializable) |
| `console.log(...)` | forwarded to the platform logger |
| `jsonui.version` | runtime version string |

## Views

Property values marked *dyn* accept dynamic values. Property values marked
*bind* are two-way: they should normally be a `"$name"` binding.

### Text and images

| type | properties |
| --- | --- |
| `Text` | `text` (dyn). Style shorthands: `font`, `bold`, `italic`, `foregroundColor`, `lineLimit`, `multilineTextAlignment` (`leading`/`center`/`trailing`) |
| `Label` | `title` (dyn), `systemImage` |
| `Image` | one of `systemName` (SF Symbol name, mapped to a Material icon on Android), `name` (asset/resource name), `url`. Optional `resizable` (bool), `contentMode` (`fit`/`fill`), `width`, `height` |
| `Link` | `title` (dyn), `url` (dyn) |
| `ProgressView` | optional `value` (dyn, 0–1) and `total`; `label` |

### Input

| type | properties |
| --- | --- |
| `TextField` | `title` (placeholder, dyn), `text` (bind), `keyboard` (`default`/`email`/`number`/`decimal`/`phone`/`url`), `autocapitalization` (`none`/`words`/`sentences`), `onCommit`, `onChange`, `error` (dyn string shown below the field when non-empty) |
| `SecureField` | `title`, `text` (bind), `onCommit`, `error` |
| `TextEditor` | `text` (bind), `minHeight`, `error` |
| `Toggle` | `label` (dyn string or node), `isOn` (bind), `onChange` |
| `Picker` | `label` (dyn), `selection` (bind), `options` (array of `{ "value": any, "label": string }` or plain strings; or a dyn expression yielding such an array), `style` (`menu`/`segmented`/`wheel`/`inline`), `onChange` |
| `DatePicker` | `label` (dyn), `selection` (bind, ISO-8601 string), `components` (`date`/`time`/`dateAndTime`), `min`, `max`, `onChange` |
| `Slider` | `value` (bind, number), `min`, `max`, `step`, `label`, `onChange` |
| `Stepper` | `label` (dyn), `value` (bind, number), `min`, `max`, `step`, `onChange` |
| `Button` | `label` (dyn string or node), `action`, `role` (`destructive`/`cancel`), `style` (`bordered`/`borderedProminent`/`plain`) |

### Layout and containers

| type | properties |
| --- | --- |
| `Form` | `content` |
| `Section` | `header` (dyn string or node), `footer` (dyn string or node), `content` |
| `List` | `content` |
| `VStack`, `HStack` | `alignment`, `spacing`, `content` |
| `ZStack` | `alignment`, `content` |
| `ScrollView` | `axis` (`vertical`/`horizontal`), `content` |
| `Group` | `content` |
| `NavigationView` | `title` (dyn), `content` |
| `Spacer` | `minLength` |
| `Divider` | |
| `ForEach` | `data` (dyn, an array; usually `"$items"`), `item` (variable name, default `item`), `index` (variable name, default `index`), `content` (template). Inside the template `${item.name}` refers to the current element. Bindings of the form `"$item.name"` write back into the array element. |
| `If` | `condition` (dyn), `content`, `else` (node or array) |
| `Custom` | any unknown type is looked up in the host's view registry. The registry receives the node and the render context. |

### Modifiers

Modifiers may be given as shorthand properties or as an ordered `modifiers`
array. Shorthand properties are applied in the order of the table below, after
the ordered array.

| property | value | notes |
| --- | --- | --- |
| `padding` | number, or `{ "top": n, "leading": n, "bottom": n, "trailing": n }`, or `{ "edges": "horizontal", "length": n }` | edges: `all`, `horizontal`, `vertical`, `top`, `bottom`, `leading`, `trailing` |
| `frame` | `{ "width", "height", "minWidth", "maxWidth", "minHeight", "maxHeight", "alignment" }` | `maxWidth: "infinity"` fills the parent |
| `font` | `largeTitle`, `title`, `title2`, `title3`, `headline`, `subheadline`, `body`, `callout`, `footnote`, `caption`, `caption2`, or `{ "size": n, "weight": "bold", "design": "monospaced" }` | |
| `bold`, `italic` | bool | |
| `foregroundColor` | color | |
| `background` | color | |
| `cornerRadius` | number | |
| `border` | `{ "color": color, "width": n }` | |
| `opacity` | number 0–1 | |
| `disabled` | bool / expression | |
| `hidden` | bool / expression | |
| `onAppear` | action | |
| `onTap` | action | |
| `accessibilityLabel` | string | |

Ordered form: `{ "modifiers": [ { "type": "padding", "length": 8 }, { "type": "background", "color": "#eeeeee" } ] }`.

Colors are `#rrggbb`, `#aarrggbb`, a named color (`red`, `blue`, `green`,
`primary`, `secondary`, `accent`, `clear`, `black`, `white`, `gray`, `orange`,
`yellow`, `pink`, `purple`), or `{ "light": color, "dark": color }`.

## Encoding from code

Both libraries include a small builder DSL that produces the same node model,
so a form can be assembled in Swift or Kotlin and serialized to this format.

## Reflecting an existing view

`JsonPreview { MyView() }` (SwiftUI) and `JsonPreview { MyComposable() }`
(Compose) derive a document from a natively built screen and show it next to
the JsonUI rendering and the JSON text, as SwiftUIJson's `JsonPreview` did.
The Swift reflector mirrors the live view hierarchy; the Android reflector
reads the composable's semantics tree. Reflected documents use the same node
types as hand written ones, with these conventions:

* state keys: `.jsonKey("name")` / `Modifier.jsonKey("name")`, else the
  `@State` property name (Swift), else `text1`, `isOn1`, `value1`, `selection1`, `date1`
* host actions for closures: `.jsonAction("name")` / `Modifier.jsonAction("name")`,
  else `action1`, `commit1`, `appear1`, `toggle1`, `setText1`, ...
* views that cannot be reflected: `{ "type": "Unsupported", "name": "<type>" }`
