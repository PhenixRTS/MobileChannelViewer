// swift-tools-version:5.3

import PackageDescription

let package = Package(
    name: "PhenixClosedCaptions",
    platforms: [.iOS(.v12), .tvOS(.v12)],
    products: [
        .library(name: "PhenixClosedCaptions",
                 targets: ["PhenixClosedCaptions"])
    ],
    targets: [
        .target(name: "PhenixClosedCaptions",
                path: "Source")
    ]
)
