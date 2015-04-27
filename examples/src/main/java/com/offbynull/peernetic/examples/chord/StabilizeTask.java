package com.offbynull.peernetic.examples.chord;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.peernetic.core.actor.Context;
import com.offbynull.peernetic.core.actor.helpers.RequestSubcoroutine;
import com.offbynull.peernetic.core.actor.helpers.SleepSubcoroutine;
import com.offbynull.peernetic.core.actor.helpers.Subcoroutine;
import com.offbynull.peernetic.core.shuttle.AddressUtils;
import com.offbynull.peernetic.examples.chord.externalmessages.GetPredecessorRequest;
import com.offbynull.peernetic.examples.chord.externalmessages.GetPredecessorResponse;
import com.offbynull.peernetic.examples.chord.externalmessages.GetSuccessorRequest;
import com.offbynull.peernetic.examples.chord.externalmessages.GetSuccessorResponse;
import com.offbynull.peernetic.examples.chord.externalmessages.GetSuccessorResponse.ExternalSuccessorEntry;
import com.offbynull.peernetic.examples.chord.externalmessages.GetSuccessorResponse.InternalSuccessorEntry;
import com.offbynull.peernetic.examples.chord.externalmessages.NotifyRequest;
import com.offbynull.peernetic.examples.chord.model.ExternalPointer;
import com.offbynull.peernetic.examples.common.nodeid.NodeId;
import com.offbynull.peernetic.examples.chord.model.InternalPointer;
import com.offbynull.peernetic.examples.chord.model.Pointer;
import com.offbynull.peernetic.examples.common.request.ExternalMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StabilizeTask implements Subcoroutine<Void> {
    
    private static final Logger LOG = LoggerFactory.getLogger(StabilizeTask.class);
    
    private final String sourceId;
    private final State state;

    public StabilizeTask(String sourceId, State state) {
        Validate.notNull(sourceId);
        Validate.notNull(state);
        this.sourceId = sourceId;
        this.state = state;
    }

    @Override
    public Void run(Continuation cnt) throws Exception {
        NodeId selfId = state.getSelfId();
        
        Context ctx = (Context) cnt.getContext();
        
        while (true) {
            funnelToSleepCoroutine(cnt, Duration.ofSeconds(1L));
            
            try {
                Pointer successor = state.getSuccessor();
                if (state.isSelfId(successor.getId())) {
                    continue;
                }

                // ask for successor's pred
                String successorAddress = ((ExternalPointer) successor).getAddress();
                
                LOG.debug("{} {} - Requesting successor's ({}) predecessor", state.getSelfId(), sourceId, successor);
                
                GetPredecessorResponse gpr = funnelToRequestCoroutine(
                        cnt,
                        successorAddress,
                        new GetPredecessorRequest(state.generateExternalMessageId()),
                        GetPredecessorResponse.class);
                
                LOG.debug("{} {} - Successor's ({}) predecessor is {}", state.getSelfId(), sourceId, successor, gpr.getChordId());

                // check to see if predecessor is between us and our successor
                if (gpr.getChordId() != null) {
                    String address = gpr.getAddress();
                    NodeId potentiallyNewSuccessorId = gpr.getChordId();
                    NodeId existingSuccessorId = ((ExternalPointer) successor).getId();

                    if (potentiallyNewSuccessorId.isWithin(selfId, false, existingSuccessorId, false)) {
                        // it is between us and our successor, so update
                        ExternalPointer newSuccessor = new ExternalPointer(potentiallyNewSuccessorId, address);
                        state.setSuccessor(newSuccessor);

                        successor = newSuccessor;
                        successorAddress = newSuccessor.getAddress();
                    }
                }

                // successor may have been updated by block above
                // ask successor for its successors
                LOG.debug("{} {} - Requesting successor's ({}) successor", state.getSelfId(), sourceId, successor);
                GetSuccessorResponse gsr = funnelToRequestCoroutine(
                        cnt,
                        successorAddress,
                        new GetSuccessorRequest(state.generateExternalMessageId()),
                        GetSuccessorResponse.class);

                List<Pointer> subsequentSuccessors = new ArrayList<>();
                gsr.getEntries().stream().map(x -> {
                    NodeId id = x.getChordId();

                    if (x instanceof InternalSuccessorEntry) {
                        return new InternalPointer(id);
                    } else if (x instanceof ExternalSuccessorEntry) {
                        return new ExternalPointer(id, ((ExternalSuccessorEntry) x).getAddress());
                    } else {
                        throw new IllegalStateException();
                    }
                }).forEachOrdered(x -> subsequentSuccessors.add(x));
                
                LOG.debug("{} {} - Successor's ({}) successor is {}", state.getSelfId(), sourceId, successor, subsequentSuccessors);

                // mark it as our new successor
                state.updateSuccessor((ExternalPointer) successor, subsequentSuccessors);
                LOG.debug("{} {} - Successors after stabilization are {}", state.getSelfId(), sourceId, state.getSuccessors());
                
                
                // notify it that we're its predecessor
                addOutgoingExternalMessage(
                        ctx,
                        successorAddress,
                        new NotifyRequest(state.generateExternalMessageId(), selfId));
                LOG.debug("{} {} - Notified {} that we're its successor", state.getSelfId(), sourceId, state.getSuccessor());
            } catch (RuntimeException re) {
                LOG.warn("Failed to stabilize", re);
            }
        }
    }
    
    @Override
    public String getSourceId() {
        return sourceId;
    }

    private void funnelToSleepCoroutine(Continuation cnt, Duration duration) throws Exception {
        new SleepSubcoroutine.Builder()
                .sourceId(sourceId)
                .timeoutDuration(duration)
                .timerAddressPrefix(state.getTimerPrefix())
                .build()
                .run(cnt);
    }

    private <T extends ExternalMessage> T funnelToRequestCoroutine(Continuation cnt, String destination, ExternalMessage message,
            Class<T> expectedResponseClass) throws Exception {
        RequestSubcoroutine<T> requestSubcoroutine = new RequestSubcoroutine.Builder<T>()
                .sourceId(AddressUtils.parentize(sourceId, "" + message.getId()))
                .destinationAddress(destination)
                .request(message)
                .timerAddressPrefix(state.getTimerPrefix())
                .addExpectedResponseType(expectedResponseClass)
                .build();
        return requestSubcoroutine.run(cnt);
    }
    
    private void addOutgoingExternalMessage(Context ctx, String destination, ExternalMessage message) {
        ctx.addOutgoingMessage(
                AddressUtils.parentize(sourceId, "" + message.getId()),
                destination,
                message);
    }
}
