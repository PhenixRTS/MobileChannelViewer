//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Combine
import PhenixClosedCaptions
import UIKit

class ViewerViewController: UIViewController, Storyboarded {
    private let closedCaptionsEnabledImage = UIImage(named: "ClosedCaptionsEnabled")
    private let closedCaptionsDisabledImage = UIImage(named: "ClosedCaptionsDisabled")
    private let offlineBackgroundColor = UIColor(patternImage: UIImage(named: "OfflineNoise")!)

    @IBOutlet private var offlineView: UIView!
    @IBOutlet private var cameraView: UIView!
    @IBOutlet private var closedCaptionsView: PhenixClosedCaptionsView!
    @IBOutlet private var closedCaptionsToggleButton: UIButton!

    private var cancellable: AnyCancellable?

    var viewModel: ViewModel!

    override func viewDidLoad() {
        super.viewDidLoad()
        offlineView.backgroundColor = offlineBackgroundColor

        assert(viewModel != nil, "ViewModel should exist!")

        cancellable = viewModel.channelStatePublisher
            .sink { [weak self] state in
                self?.onChannelStateChange(state)
            }

        viewModel.getStreamLayer = { [weak self] in
            return self?.cameraView.layer
        }
        viewModel.subscribeForChannelEvents()
        viewModel.subscribeForClosedCaptions(closedCaptionsView)
        viewModel.joinChannel()

        closedCaptionsToggleButton.isEnabled = true
    }

    private func onChannelStateChange(_ state: ViewModel.ChannelState) {
        switch state {
        case .streaming:
            offlineView.isHidden = true

        case .offline:
            offlineView.isHidden = false
        }
    }

    @IBAction
    private func closedCaptionsToggleButtonTapped(_ sender: Any) {
        viewModel.toggleClosedCaptions()
        let image = viewModel.isClosedCaptionsEnabled ? closedCaptionsEnabledImage : closedCaptionsDisabledImage
        closedCaptionsToggleButton.setImage(image, for: .normal)
    }
}
