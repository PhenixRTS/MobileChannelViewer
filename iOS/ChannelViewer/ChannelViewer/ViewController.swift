//
//  Copyright 2025 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixClosedCaptions
import PhenixDebug
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
    private var deepLinkHandled = false

    override func viewDidLoad() {
        super.viewDidLoad()
        offlineView.backgroundColor = UIColor(patternImage: UIImage(named: "OfflineNoise")!)

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            if !(self?.deepLinkHandled ?? true) {
                AppDelegate.terminate(
                    afterDisplayingAlertWithTitle: "Incorrect configuration",
                    message: "No deep link provided"
                )
            }
        }
    }

    func openDeepLink() {
        deepLinkHandled = true

        // Validate configuration
        do {
            try ValidationProvider.validate(edgeToken: PhenixConfiguration.edgeToken)
        } catch {
            DispatchQueue.main.async {
                AppDelegate.terminate(
                    afterDisplayingAlertWithTitle: "Incorrect configuration",
                    message: error.localizedDescription
                )
            }
            return
        }

        // Configure tap gesture to open debug menu, when user taps 5 times on the video surface view.
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(surfaceViewTappedMultipleTimes))
        tapGesture.numberOfTapsRequired = 5
        surfaceView.addGestureRecognizer(tapGesture)

        channelViewer.join(videoLayer: surfaceView.layer)
    }

    @IBAction func closedCaptionsToggleButtonTapped(_ sender: Any) {
        guard let closedCaptionsService = closedCaptionsService else { return }
        closedCaptionsService.isEnabled.toggle()
        let image = closedCaptionsService.isEnabled == true
            ? UIImage(named: "ClosedCaptionsEnabled")
            : UIImage(named: "ClosedCaptionsDisabled")
        closedCaptionsToggleButton.setImage(image, for: .normal)
    }

    @objc func surfaceViewTappedMultipleTimes() {
        let pcast: PhenixPCast = AppDelegate.channelExpress.roomExpress.pcastExpress.pcast
        let viewModel = DebugViewController.ViewModel(pcast: pcast)
        let viewController = DebugViewController(viewModel: viewModel)
        present(viewController, animated: true)
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
