import Foundation
import XCTest
@testable import JsonUICore

enum Samples {
    static var directory: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("samples")
    }

    static func load(_ name: String) throws -> JsonDocument {
        try JsonDocument(data: try Data(contentsOf: directory.appendingPathComponent(name)))
    }
}
