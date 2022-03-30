//
//  Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
//

import Foundation
import PhenixSdk

struct MemberNextStreamProvider {
    let streams: [PhenixStream]
    let previouslyProvidedStream: PhenixStream?

    /// A method which retrieves next possible stream and provides it.
    /// - Note: This method is not mutating, that means it will not mutate the ``previouslyProvidedStream`` property value with the newly returned stream.
    /// - Returns: Stream which should be provided next.
    func nextStream() throws -> PhenixStream {
        try nextCandidateStream()
    }

    private func nextCandidateStream() throws -> PhenixStream {
        /*
         Be sure that there is at least one
         stream in the candidate stream list
         from which to choose next stream.
         */
        guard streams.isEmpty == false else {
            throw NextStreamError.noStream
        }

        let nextCandidateStream: PhenixStream

        if let previouslyProvidedStream = previouslyProvidedStream {
            nextCandidateStream = try getNextStream(following: previouslyProvidedStream)
        } else {
            nextCandidateStream = try getNextStream()
        }

        try validateNextStream(nextCandidateStream)

        return nextCandidateStream
    }

    private func getNextStream() throws -> PhenixStream {
        /*
         We return the last stream, because it is always the newest one,
         provided by the PhenixSdk. In some edge-cases, it can be that
         the first stream in the list fails to subscribe, and then it
         will jump over to the next one - which will be the last in the
         list. Here we skip this part and always start with the newest/latest
         stream.
         */
        if let stream = streams.last {
            return stream
        } else {
            throw NextStreamError.noStream
        }
    }

    private func getNextStream(following stream: PhenixStream) throws -> PhenixStream {
        guard let streamIndex = streams.firstIndex(where: { $0.getUri() == stream.getUri() }) else {
            return try getNextStream()
        }

        guard streamIndex > streams.startIndex else {
            /*
             If we have reached the end of the stream list
             (currently, the first element of the list is the last one in the queue),
             then we need to start iterating the stream list from the beginning once
             again.
             */
            return try getNextStream()
        }

        let nextStreamIndex = streams.index(before: streamIndex)
        return streams[nextStreamIndex]
    }

    private func validateNextStream(_ stream: PhenixStream) throws {
        /*
         In case if there is only one (or none) stream in the list,
         and we are already processing it, we don't want to try to
         subscribe to it once again, because there is no point of
         doing that.
         Here we can just throw an error saying
         that we have no more streams.
         */

        if stream.getUri() == previouslyProvidedStream?.getUri() {
            if streams.count <= 1 {
                throw NextStreamError.noStream
            }
        }
    }
}

// MARK: - Error
extension MemberNextStreamProvider {
    enum NextStreamError: Error {
        case noStream
    }
}
