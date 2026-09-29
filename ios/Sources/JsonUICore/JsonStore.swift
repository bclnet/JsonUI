//
//  JsonStore.swift
//  JsonUI
//
//  The form state: an observable key/value store keyed by binding paths.
//

import Foundation

public final class JsonStore {
    public typealias Listener = (_ changedPath: JsonPath?) -> Void

    private var root: JsonValue
    private var listeners: [UUID: Listener] = [:]
    private let lock = NSRecursiveLock()

    public init(_ initial: [String: JsonValue] = [:]) {
        root = .object(initial)
    }

    public init(value: JsonValue) {
        root = value.objectValue.map { .object($0) } ?? .object([:])
    }

    // MARK: - Reading

    public var snapshot: JsonValue { lock.lock(); defer { lock.unlock() }; return root }

    public var keys: [String] { snapshot.objectValue?.keys.sorted() ?? [] }

    public func has(_ key: String) -> Bool { snapshot[key] != .null || snapshot.objectValue?[key] != nil }

    public func get(_ path: JsonPath) -> JsonValue { snapshot.value(at: path) }

    public func get(_ path: String) -> JsonValue { get(JsonPath(path)) }

    public subscript(path: String) -> JsonValue {
        get { get(path) }
        set { set(newValue, at: path) }
    }

    // MARK: - Writing

    public func set(_ value: JsonValue, at path: JsonPath) {
        lock.lock()
        let old = root.value(at: path)
        guard old != value || (old == .null && value == .null && !contains(path)) else { lock.unlock(); return }
        root.setValue(value, at: path)
        let snapshot = listeners
        lock.unlock()
        snapshot.values.forEach { $0(path) }
    }

    public func set(_ value: JsonValue, at path: String) { set(value, at: JsonPath(path)) }

    /// Merges `patch` into the top level of the state.
    public func merge(_ patch: [String: JsonValue]) {
        lock.lock()
        var object = root.objectValue ?? [:]
        var changed = false
        for (k, v) in patch where object[k] != v {
            object[k] = v
            changed = true
        }
        if changed { root = .object(object) }
        let snapshot = listeners
        lock.unlock()
        if changed { snapshot.values.forEach { $0(nil) } }
    }

    public func replace(with value: JsonValue) {
        lock.lock()
        root = value.objectValue.map { .object($0) } ?? .object([:])
        let snapshot = listeners
        lock.unlock()
        snapshot.values.forEach { $0(nil) }
    }

    private func contains(_ path: JsonPath) -> Bool {
        var current = root
        for segment in path.segments {
            switch segment {
            case .key(let k): guard let o = current.objectValue, let v = o[k] else { return false }; current = v
            case .index(let i): guard let a = current.arrayValue, i >= 0, i < a.count else { return false }; current = a[i]
            }
        }
        return true
    }

    // MARK: - Observation

    @discardableResult
    public func addListener(_ listener: @escaping Listener) -> UUID {
        let id = UUID()
        lock.lock(); listeners[id] = listener; lock.unlock()
        return id
    }

    public func removeListener(_ id: UUID) {
        lock.lock(); listeners.removeValue(forKey: id); lock.unlock()
    }
}
