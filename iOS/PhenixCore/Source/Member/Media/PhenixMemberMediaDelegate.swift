//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import AVFoundation
import Foundation

protocol MemberMediaDelegate: AnyObject {
    func audioStateDidChange(enabled: Bool)
    func videoStateDidChange(enabled: Bool)
    func audioLevelDidChange(decibel: Double)
    func videoFrameReceived(sampleBuffer: CMSampleBuffer)
}
