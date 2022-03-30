//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

protocol LocalMediaProvider: AnyObject {
    /// Retrieves current device media renderer.
    func getLocalMediaRenderer() -> PhenixRenderer?
    /// Retrieves current device media audio tracks.
    func getLocalMediaAudioTracks() -> [PhenixMediaStreamTrack]
}
