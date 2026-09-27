// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "JsonUI",
    platforms: [
        .iOS(.v15), .macOS(.v12), .tvOS(.v15), .watchOS(.v8)
    ],
    products: [
        // Platform independent model, state store, expression resolution and builder DSL.
        .library(name: "JsonUICore", targets: ["JsonUICore"]),
        // SwiftUI renderer and JavaScriptCore script engine.
        .library(name: "JsonUI", targets: ["JsonUI"]),
    ],
    targets: [
        .target(
            name: "JsonUICore",
            path: "Sources/JsonUICore"),
        .target(
            name: "JsonUI",
            dependencies: ["JsonUICore"],
            path: "Sources/JsonUI"),
        .testTarget(
            name: "JsonUICoreTests",
            dependencies: ["JsonUICore"],
            path: "Tests/JsonUICoreTests"),
        .testTarget(
            name: "JsonUITests",
            dependencies: ["JsonUI"],
            path: "Tests/JsonUITests"),
    ]
)
