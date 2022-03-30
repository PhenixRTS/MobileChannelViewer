//
//  Copyright 2022 Phenix Real Time Solutions, Inc.. Confidential and Proprietary. All rights reserved.
//

import Foundation

/// Default deep link model, containing all the possible deep link properties.
public struct PhenixDeeplinkModel {
    /// Authentication token.
    public var authToken: String?

    /// Local media publishing token.
    public var publishToken: String?

    /// Remote stream viewing token
    ///
    /// For example, to be able to view (subscribe to) remote member audio and video streams.
    public var streamToken: String?

    /// A list of channel aliases.
    public var channelAliases: [String]?

    /// A list of channel stream tokens.
    /// - Requires: List item count must match the number of items in the ``channelAliases`` array.
    public var channelStreamTokens: [String]?

    /// A list of room aliases.
    public var roomAliases: [String]?

    /// Remote audio-only stream viewing token
    ///
    /// For example, to be able to view (subscribe to) remote member audio streams.
    public var roomAudioStreamToken: String?

    /// Remote video-only stream viewing token
    ///
    /// For example, to be able to view (subscribe to) remote member video streams.
    public var roomVideoStreamToken: String?

    /// A selected alias value, provided after the # symbol in the URL.
    ///
    /// For example, it can be the selected room alias, to which members
    /// should automatically join.
    public var selectedAlias: String?

    /// List of a remote stream IDs.
    public var streamIDs: [String]?

    /// List of act times for archived stream playback positions.
    public var acts: [String]?
}

// MARK: - PhenixDeeplinkUrlModelProvider
extension PhenixDeeplinkModel: PhenixDeeplinkUrlModelRepresentable {
    /// Initialize a model, containing deep link parameter values.
    public init?(components: URLComponents) {
        if let queryItems = components.queryItems {
            let query = Dictionary(uniqueKeysWithValues: queryItems.compactMap { item -> (String, String)? in
                if let value = item.value {
                    return (item.name, value)
                } else {
                    return nil
                }
            })

            // `edgeToken` is deprecated.
            // The `edgeToken` parameter now is replaced with the `token` parameter.
            if let value = query["token"] ?? query["edgeToken"] {
                // The `token` parameter can be used both for
                // authentication and for stream viewing
                self.authToken = value
                self.streamToken = value
            }

            if let value = query["authToken"] {
                self.authToken = value
            }

            self.publishToken = query["publishToken"]

            self.roomAliases = query["roomAliases"]?
                .split(separator: ",")
                .map(String.init)

            self.roomAudioStreamToken = query["roomAudioToken"]
            self.roomVideoStreamToken = query["roomVideoToken"]

            self.channelAliases = query["channelAliases"]?
                .split(separator: ",")
                .map(String.init)

            self.channelStreamTokens = query["channelTokens"]?
                .split(separator: ",")
                .map(String.init)

            self.streamIDs = query["streamIDs"]?
                .split(separator: ",")
                .map(String.init)

            self.acts = query["acts"]?
                .split(separator: ",")
                .map(String.init)
        }

        self.selectedAlias = components.fragment
    }
}
