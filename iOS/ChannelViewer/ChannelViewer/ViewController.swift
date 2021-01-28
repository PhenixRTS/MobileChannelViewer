//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixClosedCaptions
import PhenixSdk
import UIKit

class ViewController: UIViewController {
    @IBOutlet private var offlineView: UIView!
    @IBOutlet private var surfaceView: UIView!
    @IBOutlet private var closedCaptionsView: PhenixClosedCaptionsView!
    @IBOutlet private var closedCaptionsToggleButton: UIButton!

    private lazy var channelViewer: PhenixChannelViewer = {
        let viewer = PhenixChannelViewer(channelExpress: AppDelegate.channelExpress)
        viewer.delegate = self
        return viewer
    }()

    private var currentRoomService: PhenixRoomService?
    private var closedCaptionsService: PhenixClosedCaptionsService?

    /// Channel alias, to which the user will be connected.
    public var channelAlias: String?

    override func viewDidLoad() {
        super.viewDidLoad()
        offlineView.backgroundColor = UIColor(patternImage: UIImage(named: "OfflineNoise")!)

        if PhenixConfiguration.backendUri == nil && PhenixConfiguration.edgeToken == nil {
            DispatchQueue.main.async {
                AppDelegate.terminate(
                    afterDisplayingAlertWithTitle: "Incorrect configuration.",
                    message: "No configuration provided."
                )
            }
            return
        }

        if PhenixConfiguration.backendUri != nil && PhenixConfiguration.edgeToken != nil {
            DispatchQueue.main.async {
                AppDelegate.terminate(
                    afterDisplayingAlertWithTitle: "Incorrect configuration.",
                    message: "You must provide EdgeToken or the backend url, both cannot be simultaneously provided."
                )
            }
            return
        }

        if PhenixConfiguration.edgeToken == nil && channelAlias == nil {
            DispatchQueue.main.async {
                AppDelegate.terminate(
                    afterDisplayingAlertWithTitle: "Incorrect configuration.",
                    message: "You must provide the channel alias."
                )
            }
            return
        }

        channelViewer.join(channelAlias: channelAlias, videoLayer: surfaceView.layer)
    }

    @IBAction func closedCaptionsToggleButtonTapped(_ sender: Any) {
        guard let closedCaptionsService = closedCaptionsService else { return }
        closedCaptionsService.isEnabled.toggle()
        let image = closedCaptionsService.isEnabled == true ? UIImage(named: "ClosedCaptionsEnabled") : UIImage(named: "ClosedCaptionsDisabled")
        closedCaptionsToggleButton.setImage(image, for: .normal)
    }
}

extension ViewController: PhenixChannelViewerDelegate {
    func channelViewer(_ channelViewer: PhenixChannelViewer, didJoin roomService: PhenixRoomService) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.currentRoomService = roomService
            self.closedCaptionsService = PhenixClosedCaptionsService(roomService: roomService)
            self.closedCaptionsService?.setContainerView(self.closedCaptionsView)
            self.closedCaptionsToggleButton.isEnabled = true
        }
    }

    func channelViewer(_ channelViewer: PhenixChannelViewer, didSubscribeWith subscriber: PhenixExpressSubscriber, renderer: PhenixRenderer) {
        DispatchQueue.main.async { [weak self] in
            self?.offlineView.isHidden = true
        }
    }

    func channelViewerHasNoActiveStream(_ channelViewer: PhenixChannelViewer) {
        DispatchQueue.main.async { [weak self] in
            self?.offlineView.isHidden = false
        }
    }

    func channelViewer(_ channelViewer: PhenixChannelViewer, didFailToJoinWith error: PhenixChannelViewer.Error) {
        DispatchQueue.main.async {
            AppDelegate.terminate(
                afterDisplayingAlertWithTitle: "Something went wrong!",
                message: "Application failed to join the channel (\(error.reason))."
            )
        }
    }

    func channelViewer(_ channelViewer: PhenixChannelViewer, didFailToSubscribeWith error: PhenixChannelViewer.Error) {
        DispatchQueue.main.async {
            AppDelegate.terminate(
                afterDisplayingAlertWithTitle: "Something went wrong!",
                message: "Application failed to subscribe to the channel (\(error.reason))."
            )
        }
    }
}
