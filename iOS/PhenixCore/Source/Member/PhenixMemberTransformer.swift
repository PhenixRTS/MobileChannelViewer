//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import PhenixSdk

extension PhenixCore {
    final class MemberTransformer {
        private let roomService: PhenixRoomService
        private let publisherQueue: DispatchQueue

        init(roomService: PhenixRoomService, publisherQueue: DispatchQueue) {
            self.roomService = roomService
            self.publisherQueue = publisherQueue
        }

        func transform(_ member: PhenixMember) -> Member {
            makeRoomMember(member)
        }

        func transform(_ members: [PhenixMember]) -> [Member] {
            members.map(makeRoomMember)
        }
    }
}

private extension PhenixCore.MemberTransformer {
    func makeRoomMember(_ member: PhenixMember) -> PhenixCore.Member {
        if let selfMember = roomService.getSelf(), member.getSessionId() == selfMember.getSessionId() {
            return PhenixCore.Member(phenixMember: member, isSelf: true, publisherQueue: publisherQueue)
        } else {
            return PhenixCore.Member(phenixMember: member, isSelf: false, publisherQueue: publisherQueue)
        }
    }
}
