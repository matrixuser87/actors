package com.offbynull.peernetic.demos.chord.fsms;

import com.offbynull.peernetic.common.message.ByteArrayNonce;
import com.offbynull.peernetic.common.ProcessableUtils;
import com.offbynull.peernetic.common.identification.Id;
import com.offbynull.peernetic.common.message.Nonce;
import com.offbynull.peernetic.common.message.NonceManager;
import com.offbynull.peernetic.common.message.Response;
import com.offbynull.peernetic.demos.chord.ChordContext;
import com.offbynull.peernetic.demos.chord.core.ChordUtils;
import com.offbynull.peernetic.demos.chord.core.ExternalPointer;
import com.offbynull.peernetic.demos.chord.messages.external.GetClosestPrecedingFingerRequest;
import com.offbynull.peernetic.demos.chord.messages.external.GetClosestPrecedingFingerResponse;
import com.offbynull.peernetic.demos.chord.messages.external.GetIdRequest;
import com.offbynull.peernetic.demos.chord.messages.external.GetIdResponse;
import com.offbynull.peernetic.demos.chord.messages.external.GetSuccessorRequest;
import com.offbynull.peernetic.demos.chord.messages.external.GetSuccessorResponse;
import com.offbynull.peernetic.fsm.FilterHandler;
import com.offbynull.peernetic.fsm.FiniteStateMachine;
import com.offbynull.peernetic.fsm.StateHandler;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import org.apache.commons.lang3.Validate;

public final class RouteToFinger<A> {
    public static final String INITIAL_STATE = "start";
    public static final String AWAIT_PREDECESSOR_RESPONSE_STATE = "pred_await";
    public static final String AWAIT_SUCCESSOR_RESPONSE_STATE = "succ_resp";
    public static final String AWAIT_ID_RESPONSE_STATE = "id_resp";
    public static final String DONE_STATE = "done";

    private static final Duration TIMER_DURATION = Duration.ofSeconds(3L);

    private final Id findId;
    
    private Id foundId;
    private A foundAddress;
    
    private ExternalPointer<A> currentNode;


    public RouteToFinger(ExternalPointer<A> initialNode, Id findId) {
        Validate.notNull(initialNode);
        Validate.notNull(findId);

        this.findId = findId;
        this.currentNode = initialNode;
    }
    
    @StateHandler(INITIAL_STATE)
    public void handleStart(FiniteStateMachine fsm, Instant time, Object unused, ChordContext<A> context)
            throws Exception {
        byte[] idData = findId.getValueAsByteArray();
        context.getOutgoingRequestManager().sendRequestAndTrack(time, new GetClosestPrecedingFingerRequest(idData),
                currentNode.getAddress());
        fsm.setState(AWAIT_PREDECESSOR_RESPONSE_STATE);
        
        if (context.getSelfId().getValueAsBigInteger().equals(BigInteger.ONE)) {
            System.out.println("Starting find of " + findId);
        }
        
        context.getEndpointScheduler().scheduleMessage(TIMER_DURATION, context.getSelfEndpoint(), context.getSelfEndpoint(),
                new TimerTrigger());
    }

    @FilterHandler({AWAIT_PREDECESSOR_RESPONSE_STATE, AWAIT_SUCCESSOR_RESPONSE_STATE, AWAIT_ID_RESPONSE_STATE})
    public boolean filterResponses(FiniteStateMachine fsm, Instant time, Response response, ChordContext<A> context)
            throws Exception {
        return context.getOutgoingRequestManager().isMessageTracked(time, response);
    }

    private NonceManager<byte[]> nonceManager = new NonceManager<>();
    @StateHandler(AWAIT_PREDECESSOR_RESPONSE_STATE)
    public void handleFindPredecessorResponse(FiniteStateMachine fsm, Instant time,
            GetClosestPrecedingFingerResponse<A> response, ChordContext<A> context) throws Exception {
        A address = response.getAddress();
        byte[] idData = response.getId();
        
        Id id = new Id(idData, currentNode.getId().getLimitAsByteArray());
        
        if (context.getSelfId().getValueAsBigInteger().equals(BigInteger.ONE)) {
            System.out.println("Closest pred to " + findId + " returned by " + currentNode.getId() + " is " + id);
        }
        
        if (id.equals(currentNode.getId()) && address == null) {
            // findId's predecessor is the queried node
            Nonce<byte[]> nonce = context.getOutgoingRequestManager().sendRequestAndTrack(time, new GetSuccessorRequest(),
                    currentNode.getAddress());
            nonceManager.addNonce(time, Duration.ofSeconds(30L), nonce, null);
            fsm.setState(AWAIT_SUCCESSOR_RESPONSE_STATE);
        } else if (!id.equals(currentNode.getId()) && address != null) {
            ExternalPointer<A> nextNode = new ExternalPointer<>(id, address);
            currentNode = nextNode;
            if (findId.isWithin(currentNode.getId(), false, nextNode.getId(), true)) {
                // node found, stop here
                Nonce<byte[]> nonce = context.getOutgoingRequestManager().sendRequestAndTrack(time, new GetSuccessorRequest(),
                        currentNode.getAddress());
                nonceManager.addNonce(time, Duration.ofSeconds(30L), nonce, null);
                fsm.setState(AWAIT_SUCCESSOR_RESPONSE_STATE);
            } else {
                context.getOutgoingRequestManager().sendRequestAndTrack(time, new GetClosestPrecedingFingerRequest(
                        findId.getValueAsByteArray()), currentNode.getAddress());
                fsm.setState(AWAIT_PREDECESSOR_RESPONSE_STATE);
            }
        } else {
            // we have a node id that isn't current node and no address, node gave us bad response so try again
            context.getOutgoingRequestManager().sendRequestAndTrack(time, new GetClosestPrecedingFingerRequest(
                    findId.getValueAsByteArray()), currentNode.getAddress());
            fsm.setState(AWAIT_PREDECESSOR_RESPONSE_STATE);
        }
    }

    @StateHandler(AWAIT_SUCCESSOR_RESPONSE_STATE)
    public void handleSuccessorResponse(FiniteStateMachine fsm, Instant time,
            GetSuccessorResponse<A> response, ChordContext<A> context) throws Exception {
        ByteArrayNonce nonce = new ByteArrayNonce(response.getNonce());
        if (!nonceManager.isNoncePresent(nonce)) {
            return;
        }
        
        A senderAddress = context.getEndpointIdentifier().identify(context.getSourceEndpoint());

        A address = response.getAddress();
        if (address == null) {
            if (context.getSelfId().getValueAsBigInteger().equals(BigInteger.ONE)) {
                System.out.println("Successor returned by " + currentNode.getId() + " is itself " + currentNode.getId());
            }

            address = senderAddress;
        }
        
        if (context.getSelfId().getValueAsBigInteger().equals(BigInteger.ONE)) {
            System.out.println("Successor returned by " + currentNode.getId() + " is at address " + address);
        }
        

        Nonce<byte[]> newNonce = context.getOutgoingRequestManager().sendRequestAndTrack(time, new GetIdRequest(), address);
        nonceManager.addNonce(time, Duration.ofSeconds(30L), newNonce, null);
        nonceManager.removeNonce(nonce);
        fsm.setState(AWAIT_ID_RESPONSE_STATE);
    }

    @StateHandler(AWAIT_ID_RESPONSE_STATE)
    public void handleAskForIdResponse(FiniteStateMachine fsm, Instant time, GetIdResponse response,
            ChordContext<A> context) throws Exception {
        ByteArrayNonce nonce = new ByteArrayNonce(response.getNonce());
        if (!nonceManager.isNoncePresent(nonce)) {
            return;
        }
        
        int bitSize = ChordUtils.getBitLength(findId);
        foundId = new Id(response.getId(), bitSize);
        foundAddress = context.getEndpointIdentifier().identify(context.getSourceEndpoint());
        
        if (context.getSelfId().getValueAsBigInteger().equals(BigInteger.ONE)) {
            System.out.println("Found " + findId + " at " + foundId);
        }

        fsm.setState(DONE_STATE);
    }

    @StateHandler({AWAIT_PREDECESSOR_RESPONSE_STATE, AWAIT_SUCCESSOR_RESPONSE_STATE, AWAIT_ID_RESPONSE_STATE})
    public void handleTimerTrigger(FiniteStateMachine fsm, Instant time, TimerTrigger message, ChordContext<A> context) {
        if (!message.checkParent(this)) {
            return;
        }
        
        Duration ormDuration = context.getOutgoingRequestManager().process(time);
        
        if (context.getOutgoingRequestManager().getPending() == 0) {
            fsm.setState(DONE_STATE);
            return;
        }
        
        Duration nextDuration = ProcessableUtils.scheduleEarliestDuration(ormDuration, TIMER_DURATION);
        context.getEndpointScheduler().scheduleMessage(nextDuration, context.getSelfEndpoint(), context.getSelfEndpoint(),
                new TimerTrigger());
    }

    public ExternalPointer<A> getResult() {
        if (foundId == null || foundAddress == null) {
            return null;
        }
        
        return new ExternalPointer<>(foundId, foundAddress);
    }

    public final class TimerTrigger {
        private TimerTrigger() {
            // does nothing, prevents outside instantiation
        }
        
        public boolean checkParent(Object obj) {
            return RouteToFinger.this == obj;
        }
    }
}
