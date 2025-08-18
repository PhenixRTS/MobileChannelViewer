//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import AVKit

extension CMSampleBuffer {
    func makeFrameDisplayableImmediately() {
        guard let attachments = CMSampleBufferGetSampleAttachmentsArray(self, createIfNecessary: true) else { return }

        for attachment in attachments as NSArray {
            if let dict = attachment as? NSMutableDictionary {
                dict[kCMSampleAttachmentKey_DisplayImmediately as String] = true
            }
        }
    }
}
