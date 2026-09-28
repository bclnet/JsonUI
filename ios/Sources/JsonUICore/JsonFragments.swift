//
//  JsonFragments.swift
//  JsonUI
//
//  Fragments: pieces of JSON reused by reference. `{ "$ref": "shared.json#/sections/address" }`
//  is replaced by the value the JSON pointer names in that document, and
//  `{ "$ref": "#header" }` by `_ui.fragments.header` of the same document.
//  Keys beside `$ref` override the fragment's keys. Resolution happens before
//  a document is parsed, so every consumer (forms, scenes, minds, glyphs)
//  sees plain JSON.
//

import Foundation

public enum JsonFragmentError: Error, Equatable, CustomStringConvertible {
    case missingDocument(String)
    case missingFragment(String)
    case cycle(String)
    case tooDeep

    public var description: String {
        switch self {
        case .missingDocument(let s): return "fragment document not loaded: \(s)"
        case .missingFragment(let s): return "fragment not found: \(s)"
        case .cycle(let s): return "fragment refers to itself: \(s)"
        case .tooDeep: return "fragments nested too deeply"
        }
    }
}

public struct JsonFragmentReference: Equatable {
    /// The document the fragment lives in; `nil` means the referring document.
    public var url: URL?
    /// JSON pointer (RFC 6901) into that document; `""` is the whole document.
    public var pointer: String

    public static let refKey = "$ref"
    public static let fragmentsPointer = "/_ui/fragments/"

    /// Parses `"shared.json#/a/b"`, `"#/a/b"`, `"#name"` (→ `_ui.fragments.name`) or `"shared.json"`.
    public init(_ text: String, base: URL?) {
        let parts = text.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false)
        let location = String(parts[0])
        var fragment = parts.count > 1 ? String(parts[1]).removingPercentEncoding ?? String(parts[1]) : ""
        if !fragment.isEmpty, !fragment.hasPrefix("/") { fragment = JsonFragmentReference.fragmentsPointer + fragment }
        pointer = fragment
        if location.isEmpty {
            url = nil
        } else if let absolute = URL(string: location), absolute.scheme != nil {
            url = absolute
        } else {
            url = base.flatMap { URL(string: location, relativeTo: $0)?.absoluteURL } ?? URL(string: location)
        }
    }

    public var key: String { (url?.absoluteString ?? "") + "#" + pointer }
}

public final class JsonFragmentResolver {
    public typealias Loader = (URL) throws -> JsonValue

    /// Loads a document on demand (synchronously). Without one, every referenced document must be registered first.
    public var loader: Loader?
    public private(set) var documents: [URL: JsonValue] = [:]
    public var maxDepth = 32

    public init(loader: Loader? = nil) { self.loader = loader }

    /// Makes a fetched document available under its URL (the fragment part is ignored).
    public func register(_ value: JsonValue, for url: URL) { documents[JsonFragmentResolver.documentURL(url)] = value }

    public func document(for url: URL) -> JsonValue? { documents[JsonFragmentResolver.documentURL(url)] }

    static func documentURL(_ url: URL) -> URL {
        guard url.fragment != nil, var components = URLComponents(url: url, resolvingAgainstBaseURL: true) else { return url.absoluteURL }
        components.fragment = nil
        return components.url ?? url
    }

    /// Replaces every `$ref` in `value` (a document at `base`) by the fragment it names.
    public func resolve(_ value: JsonValue, base: URL? = nil) throws -> JsonValue {
        try resolve(value, root: value, base: base, stack: [], depth: 0)
    }

    /// The documents that `value` refers to and that are not loaded yet. An app fetches them,
    /// registers them and calls again until the list is empty; then `resolve` cannot fail on a missing document.
    public func externalReferences(in value: JsonValue, base: URL? = nil) -> [URL] {
        var found: [URL] = []
        var seen = Set<URL>()
        var visitedDocuments = Set<URL>()
        func walk(_ v: JsonValue, base: URL?) {
            switch v {
            case .object(let o):
                if let ref = o[JsonFragmentReference.refKey]?.text, let url = JsonFragmentReference(ref, base: base).url {
                    let doc = JsonFragmentResolver.documentURL(url)
                    if let loaded = documents[doc] {
                        if !visitedDocuments.contains(doc) { visitedDocuments.insert(doc); walk(loaded, base: doc) }
                    } else if !seen.contains(doc) {
                        seen.insert(doc)
                        found.append(doc)
                    }
                }
                for (_, child) in o { walk(child, base: base) }
            case .array(let a):
                for item in a { walk(item, base: base) }
            default: break
            }
        }
        walk(value, base: base)
        return found
    }

    private func resolve(_ value: JsonValue, root: JsonValue, base: URL?, stack: [String], depth: Int) throws -> JsonValue {
        guard depth <= maxDepth else { throw JsonFragmentError.tooDeep }
        switch value {
        case .object(let object):
            if let ref = object[JsonFragmentReference.refKey]?.text {
                return try expand(ref, overrides: object, root: root, base: base, stack: stack, depth: depth)
            }
            var out: [String: JsonValue] = [:]
            for (k, v) in object { out[k] = try resolve(v, root: root, base: base, stack: stack, depth: depth + 1) }
            return .object(out)
        case .array(let items):
            var out: [JsonValue] = []
            for item in items {
                let resolved = try resolve(item, root: root, base: base, stack: stack, depth: depth + 1)
                // A reference to a list of nodes is spliced into the surrounding list.
                if item.objectValue?[JsonFragmentReference.refKey] != nil, case .array(let spliced) = resolved { out.append(contentsOf: spliced) }
                else { out.append(resolved) }
            }
            return .array(out)
        default:
            return value
        }
    }

    private func expand(_ ref: String, overrides: [String: JsonValue], root: JsonValue, base: URL?, stack: [String], depth: Int) throws -> JsonValue {
        let reference = JsonFragmentReference(ref, base: base)
        let key = reference.url == nil ? (base?.absoluteString ?? "") + "#" + reference.pointer : reference.key
        guard !stack.contains(key) else { throw JsonFragmentError.cycle(ref) }
        var targetRoot = root
        var targetBase = base
        if let url = reference.url {
            let doc = JsonFragmentResolver.documentURL(url)
            if let loaded = documents[doc] {
                targetRoot = loaded
            } else if let loader = loader {
                let loaded = try loader(doc)
                documents[doc] = loaded
                targetRoot = loaded
            } else {
                throw JsonFragmentError.missingDocument(doc.absoluteString)
            }
            targetBase = doc
        }
        guard let fragment = JsonFragmentResolver.value(at: reference.pointer, in: targetRoot) else { throw JsonFragmentError.missingFragment(ref) }
        var resolved = try resolve(fragment, root: targetRoot, base: targetBase, stack: stack + [key], depth: depth + 1)
        let extra = overrides.filter { $0.key != JsonFragmentReference.refKey }
        if !extra.isEmpty, case .object(var merged) = resolved {
            for (k, v) in extra {
                let value = try resolve(v, root: root, base: base, stack: stack, depth: depth + 1)
                if value.isNull { merged.removeValue(forKey: k) } else { merged[k] = value }
            }
            resolved = .object(merged)
        }
        return resolved
    }

    /// RFC 6901 JSON pointer lookup (`""` is the whole value; `~1` and `~0` escape `/` and `~`).
    public static func value(at pointer: String, in value: JsonValue) -> JsonValue? {
        if pointer.isEmpty { return value }
        guard pointer.hasPrefix("/") else { return nil }
        var current = value
        for raw in pointer.dropFirst().split(separator: "/", omittingEmptySubsequences: false) {
            let token = raw.replacingOccurrences(of: "~1", with: "/").replacingOccurrences(of: "~0", with: "~")
            switch current {
            case .object(let o):
                guard let next = o[token] else { return nil }
                current = next
            case .array(let a):
                guard let i = Int(token), i >= 0, i < a.count else { return nil }
                current = a[i]
            default:
                return nil
            }
        }
        return current
    }
}

extension JsonValue {
    /// The string only when the value is a JSON string (`stringValue` renders any value as text).
    public var text: String? { if case .string(let s) = self { return s } else { return nil } }
    /// The number only when the value is a JSON number.
    public var numberValue: Double? { if case .number(let n) = self { return n } else { return nil } }
    /// The integer only when the value is an integral JSON number.
    public var integerValue: Int? { guard let n = numberValue, n == n.rounded(), abs(n) < 1e15 else { return nil }; return Int(n) }
    /// The bool only when the value is a JSON bool.
    public var flag: Bool? { if case .bool(let b) = self { return b } else { return nil } }

    /// Whether this value contains any `$ref`.
    public var hasFragmentReferences: Bool {
        switch self {
        case .object(let o): return o[JsonFragmentReference.refKey]?.text != nil || o.values.contains { $0.hasFragmentReferences }
        case .array(let a): return a.contains { $0.hasFragmentReferences }
        default: return false
        }
    }
}

extension JsonDocument {
    /// Parses a document after resolving its fragments with `resolver` (`base` is the document's own URL).
    public init(value: JsonValue, base: URL?, resolver: JsonFragmentResolver) throws {
        try self.init(value: try resolver.resolve(value, base: base))
    }
}
