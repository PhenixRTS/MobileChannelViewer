//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import AVFoundation
import Combine

class VideoLayer: AVSampleBufferDisplayLayer {
    private var cancellable: AnyCancellable?

    /// Sets current layer s a sublayer to the provided destination layer.
    /// - Parameter destinationLayer: Destination layer on which to set the current layer.
    ///   If the destination layer is not provided, then just remove the current layer from its super layer, if it belongs to one.
    func set(on destinationLayer: CALayer?) {
        cancellable = destinationLayer?.publisher(for: \.bounds, options: [.initial, .new])
            .receive(on: DispatchQueue.main)
            .sink { [weak self] rect in
                CATransaction.withoutAnimations { self?.frame = rect }
            }

        DispatchQueue.main.async {
            self.removeFromSuperlayer()

            if let layer = destinationLayer {
                layer.addSublayer(self)
            }
        }
    }
}
