// swift-tools-version:5.3

import PackageDescription

let package = Package(
    name: "PhenixDebug",
    platforms: [.iOS(.v12), .tvOS(.v12)],
    products: [
        .library(name: "PhenixDebug",
                 targets: ["PhenixDebug"])
    ],
    targets: [
        .target(name: "PhenixDebug",
                path: "Source")
    ]
)
