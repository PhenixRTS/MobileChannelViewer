// swift-tools-version:5.3

import PackageDescription

let package = Package(
    name: "PhenixDeeplink",
    platforms: [.iOS(.v12), .tvOS(.v12)],
    products: [
        .library(name: "PhenixDeeplink",
                 targets: ["PhenixDeeplink"])
    ],
    targets: [
        .target(name: "PhenixDeeplink",
                path: "Source")
    ]
)
