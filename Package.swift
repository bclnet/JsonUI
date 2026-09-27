// swift-tools-version:5.9
import PackageDescription

// The manifest must stay at the repository root so the package can be added by URL;
// the Swift sources live in ios/ next to the Android project in android/.
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
            path: "ios/Sources/JsonUICore"),
        .target(
            name: "JsonUI",
            dependencies: ["JsonUICore"],
            path: "ios/Sources/JsonUI"),
        .testTarget(
            name: "JsonUICoreTests",
            dependencies: ["JsonUICore"],
            path: "ios/Tests/JsonUICoreTests"),
        .testTarget(
            name: "JsonUITests",
            dependencies: ["JsonUI"],
            path: "ios/Tests/JsonUITests"),
    ]
)
