//
//  Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk

public protocol PhenixChannelViewerDelegate: AnyObject {
    func channelViewerHasNoActiveStream(_ channelViewer: PhenixChannelViewer)
    func channelViewer(_ channelViewer: PhenixChannelViewer, didJoin roomService: PhenixRoomService)
    func channelViewer(_ channelViewer: PhenixChannelViewer, didSubscribeWith subscriber: PhenixExpressSubscriber, renderer: PhenixRenderer)
    func channelViewer(_ channelViewer: PhenixChannelViewer, didFailToJoinWith error: PhenixChannelViewer.Error)
    func channelViewer(_ channelViewer: PhenixChannelViewer, didFailToSubscribeWith error: PhenixChannelViewer.Error)
}
