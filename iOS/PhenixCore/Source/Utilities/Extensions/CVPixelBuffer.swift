//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import CoreMedia.CMSampleBuffer
import CoreVideo.CVPixelBuffer

extension CVPixelBuffer {
    func createSampleBufferFrame() -> CMSampleBuffer? {
        var sampleTimingInfo = CMSampleTimingInfo(
            duration: .zero,
            presentationTimeStamp: .zero,
            decodeTimeStamp: .invalid
        )

        var optionalFormatDescription: CMFormatDescription?
        CMVideoFormatDescriptionCreateForImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: self,
            formatDescriptionOut: &optionalFormatDescription
        )

        guard let formatDescription = optionalFormatDescription else {
            return nil
        }

        var optionalOutputFrame: CMSampleBuffer?
        CMSampleBufferCreateReadyWithImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: self,
            formatDescription: formatDescription,
            sampleTiming: &sampleTimingInfo,
            sampleBufferOut: &optionalOutputFrame
        )

        return optionalOutputFrame
    }
}
