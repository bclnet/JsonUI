//
//  JsonPath.swift
//  JsonUI
//
//  A path into the form state: `phones[0].number` → [key("phones"), index(0), key("number")].
//

import Foundation

public struct JsonPath: Equatable, Hashable, CustomStringConvertible {
    public enum Segment: Equatable, Hashable {
        case key(String)
        case index(Int)
    }

    public var segments: [Segment]

    public init(segments: [Segment]) { self.segments = segments }

    /// Parses `a.b[2].c`. Numeric dotted segments (`a.2.c`) are treated as indices.
    public init(_ text: String) {
        var segments: [Segment] = []
        var current = ""
        var inBracket = false
        func flush() {
            guard !current.isEmpty else { return }
            if inBracket || current.allSatisfy(\.isNumber), let i = Int(current) { segments.append(.index(i)) }
            else { segments.append(.key(current)) }
            current = ""
        }
        for ch in text {
            switch ch {
            case ".": if !inBracket { flush() } else { current.append(ch) }
            case "[": flush(); inBracket = true
            case "]": flush(); inBracket = false
            default: current.append(ch)
            }
        }
        flush()
        self.segments = segments
    }

    public var isEmpty: Bool { segments.isEmpty }

    public var first: Segment? { segments.first }

    public func appending(_ other: JsonPath) -> JsonPath { JsonPath(segments: segments + other.segments) }

    public func dropFirst() -> JsonPath { JsonPath(segments: Array(segments.dropFirst())) }

    public var description: String {
        var out = ""
        for segment in segments {
            switch segment {
            case .key(let k): out += out.isEmpty ? k : ".\(k)"
            case .index(let i): out += "[\(i)]"
            }
        }
        return out
    }
}
