import XCTest
@testable import JsonUICore

final class JsonStoreTests: XCTestCase {
    func testSetGetAndListeners() {
        let store = JsonStore(["name": "a"])
        var changes: [String] = []
        store.addListener { changes.append($0?.description ?? "*") }
        store.set("b", at: "name")
        store.set("b", at: "name") // unchanged, no notification
        store.set("x", at: "address.city")
        store.merge(["flag": true])
        XCTAssertEqual(store["name"], "b")
        XCTAssertEqual(store["address.city"], "x")
        XCTAssertEqual(store["flag"], true)
        XCTAssertEqual(changes, ["name", "address.city", "*"])
        XCTAssertEqual(store.keys, ["address", "flag", "name"])
    }

    func testRemoveListener() {
        let store = JsonStore()
        var count = 0
        let id = store.addListener { _ in count += 1 }
        store.set(1, at: "a")
        store.removeListener(id)
        store.set(2, at: "a")
        XCTAssertEqual(count, 1)
    }
}
