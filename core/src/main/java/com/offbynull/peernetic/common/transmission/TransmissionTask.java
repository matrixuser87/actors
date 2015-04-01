package com.offbynull.peernetic.common.transmission;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.peernetic.CoroutineActor.Context;
import com.offbynull.peernetic.actor.Endpoint;
import com.offbynull.peernetic.actor.EndpointDirectory;
import com.offbynull.peernetic.actor.EndpointIdentifier;
import com.offbynull.peernetic.actor.EndpointScheduler;
import com.offbynull.peernetic.common.message.Nonce;
import com.offbynull.peernetic.common.message.NonceAccessor;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 1. will send request, then resend up until some point
//   1.1 once response arrives, will stop resending
//   1.2 duplicate response arrivals will be ignored, up until some point
//   1.3 duplicate request sends will be ignored, up until some point
// 2. requests to self are ignored
// 3. responses will be cached and resent if the request for that response comes in again (because the initial response may not have made it
//      to the other side)
public final class TransmissionTask<A, N> implements Coroutine {

    private static final Logger LOG = LoggerFactory.getLogger(TransmissionTask.class);

    private final Map<Class<?>, Consumer<?>> eventHandlers;
    
    private Endpoint selfEndpoint;
    private Endpoint userEndpoint;
    private EndpointScheduler endpointScheduler;
    private EndpointDirectory<A> endpointDirectory;
    private EndpointIdentifier<A> endpointIdentifier;
    private NonceAccessor<N> nonceAccessor;

    private UnmodifiableMap<Class<?>, TypeParameters> typeParametersMapping;

    private Map<Nonce<N>, OutgoingRequestState> outgoingRequestStates;
    private Map<Nonce<N>, OutgoingResponseState> outgoingResponseStates;
    private Map<Nonce<N>, IncomingRequestState> incomingRequestStates;
    private Map<Nonce<N>, IncomingResponseState> incomingResponseStates;

    public TransmissionTask() {
        eventHandlers = new HashMap<>();
        eventHandlers.put(OutgoingRequestResendEvent.class, (Consumer<OutgoingRequestResendEvent>) this::handle);
        eventHandlers.put(OutgoingRequestDiscardEvent.class, (Consumer<OutgoingRequestDiscardEvent>) this::handle);
        eventHandlers.put(OutgoingResponseDiscardEvent.class, (Consumer<OutgoingResponseDiscardEvent>) this::handle);
        eventHandlers.put(IncomingRequestDiscardEvent.class, (Consumer<IncomingRequestDiscardEvent>) this::handle);
        eventHandlers.put(IncomingResponseDiscardEvent.class, (Consumer<IncomingResponseDiscardEvent>) this::handle);
        eventHandlers.put(OutgoingMessageEvent.class, (Consumer<OutgoingMessageEvent>) this::handle);
        eventHandlers.put(IncomingMessageEvent.class, (Consumer<IncomingMessageEvent>) this::handle);
    }
    
    @Override
    public void run(Continuation continuation) {
        Context context = (Context) continuation.getContext();
        
        Validate.validState(context.getMessage() instanceof StartEvent, "First message to this task must be of type %s", StartEvent.class);
        StartEvent<A, N> startEvent = (StartEvent<A, N>) context.getMessage();
        
        selfEndpoint = startEvent.getSelfEndpoint();
        userEndpoint = startEvent.getUserEndpoint();
        endpointScheduler = startEvent.getEndpointScheduler();
        endpointDirectory = startEvent.getEndpointDirectory();
        endpointIdentifier = startEvent.getEndpointIdentifier();
        nonceAccessor = startEvent.getNonceAccessor();
        typeParametersMapping = startEvent.getTypeParameters();
        outgoingRequestStates = new HashMap<>();
        outgoingResponseStates = new HashMap<>();
        incomingRequestStates = new HashMap<>();
        incomingResponseStates = new HashMap<>();
        
        while (true) {
            continuation.suspend();

            Object event = context.getMessage();
            Consumer<Object> handler = (Consumer<Object>) eventHandlers.get(event.getClass());
            
            Validate.validState(handler != null, "Unrecognized event type %s", event);
            
            handler.accept(event);
        }
    }

    private void handle(OutgoingRequestResendEvent event) {
        OutgoingRequestResendEvent<N> reqResendEvent = (OutgoingRequestResendEvent<N>) event;
        Nonce<N> nonce = reqResendEvent.getNonce();

        OutgoingRequestState<N> requestState = outgoingRequestStates.get(nonce);
        if (requestState == null) {
            return;
        }
        
        if (incomingResponseStates.containsKey(nonce)) {
            LOG.debug("Ignoring resend event since response has already arrived");
            return;
        }

        requestState.incrementSendCount();
        
        Duration resendDuration = requestState.getNextResendDuration();
        Object resendEvent = requestState.getNextResendEvent();
        if (resendDuration != null && resendEvent != null) {
            endpointScheduler.scheduleMessage(resendDuration, selfEndpoint, selfEndpoint, resendEvent);
        }

        Endpoint dstEndpoint = requestState.getEndpoint();
        Object msg = requestState.getMessage();
        dstEndpoint.send(selfEndpoint, msg);
    }

    private void handle(OutgoingRequestDiscardEvent event) {
        OutgoingRequestDiscardEvent<N> discardEvent = (OutgoingRequestDiscardEvent<N>) event;
        Nonce<N> nonce = discardEvent.getNonce();
        outgoingRequestStates.remove(nonce);
    }

    private void handle(OutgoingResponseDiscardEvent event) {
        OutgoingResponseDiscardEvent<N> discardEvent = (OutgoingResponseDiscardEvent<N>) event;
        Nonce<N> nonce = discardEvent.getNonce();
        outgoingResponseStates.remove(nonce);
    }

    private void handle(IncomingRequestDiscardEvent event) {
        IncomingRequestDiscardEvent<N> discardEvent = (IncomingRequestDiscardEvent<N>) event;
        Nonce<N> nonce = discardEvent.getNonce();
        incomingRequestStates.remove(nonce);
    }

    private void handle(IncomingResponseDiscardEvent event) {
        IncomingResponseDiscardEvent<N> discardEvent = (IncomingResponseDiscardEvent<N>) event;
        Nonce<N> nonce = discardEvent.getNonce();
        incomingResponseStates.remove(nonce);
    }

    private void handle(OutgoingMessageEvent event) {
        OutgoingMessageEvent<A> outgoingMsgEvent = (OutgoingMessageEvent<A>) event;
        Object innerMsg = outgoingMsgEvent.getMessage();
        A address = outgoingMsgEvent.getAddress();

        Nonce<N> nonce;
        try {
            nonce = nonceAccessor.get(innerMsg);
        } catch (IllegalArgumentException iae) {
            throw new IllegalStateException("Error extracting nonce, dropping outgoing message: " + innerMsg, iae);
        }

        TypeParameters typeParameters = typeParametersMapping.get(innerMsg.getClass());
        Validate.validState(typeParameters != null, "No type mapping found for outgoing message %s", innerMsg);
        OutgoingRequestTypeParameters requestParams;
        OutgoingResponseTypeParameters responseParams;

        if ((requestParams = typeParameters.getOutgoingRequestTypeParameters()) != null) {
            if (outgoingRequestStates.containsKey(nonce)) {
                LOG.warn("Request with nonce already sent, ignoring: {}", innerMsg);
                return;
            }

            Endpoint dstEndpoint = endpointDirectory.lookup(address);
            OutgoingRequestState<N> outgoingState = new OutgoingRequestState<>(requestParams, dstEndpoint, nonce, innerMsg);
            outgoingRequestStates.put(nonce, outgoingState);

            outgoingState.incrementSendCount();
            
            Duration nextResendDuration = outgoingState.getNextResendDuration();
            Object nextResendEvent = outgoingState.getNextResendEvent();
            if (nextResendDuration != null || nextResendEvent != null) {
                endpointScheduler.scheduleMessage(nextResendDuration, selfEndpoint, selfEndpoint, nextResendEvent);    
            }
            
            Duration discardDuration = outgoingState.getDiscardDuration();
            Object discardEvent = outgoingState.getDiscardEvent();
            endpointScheduler.scheduleMessage(discardDuration, selfEndpoint, selfEndpoint, discardEvent);    
            
            dstEndpoint.send(selfEndpoint, innerMsg);
        } else if ((responseParams = typeParameters.getOutgoingResponseTypeParameters()) != null) {
            Validate.validState(!outgoingResponseStates.containsKey(nonce), "Response already sent %s", innerMsg);

            Endpoint dstEndpoint = endpointDirectory.lookup(address);
            OutgoingResponseState<N> outgoingState = new OutgoingResponseState<>(responseParams, dstEndpoint, nonce, innerMsg);
            outgoingResponseStates.put(nonce, outgoingState);

            Duration discardDuration = outgoingState.getDiscardDuration();
            Object discardEvent = outgoingState.getDiscardEvent();

            dstEndpoint.send(selfEndpoint, innerMsg);
            endpointScheduler.scheduleMessage(discardDuration, selfEndpoint, selfEndpoint, discardEvent);
        } else {
            throw new IllegalStateException("Unsupported outgoing message " + innerMsg);
        }
    }

    private void handle(IncomingMessageEvent event) {
        IncomingMessageEvent<A> incomingMsgEvent = (IncomingMessageEvent<A>) event;
        Object innerMsg = incomingMsgEvent.getMessage();
        A address = incomingMsgEvent.getAddress();

        Nonce<N> nonce;
        try {
            nonce = nonceAccessor.get(innerMsg);
        } catch (IllegalArgumentException iae) {
            LOG.warn("Error extracting nonce, dropping incoming message: {}", innerMsg);
            return;
        }

        TypeParameters typeParameters = typeParametersMapping.get(innerMsg.getClass());
        if (typeParameters == null) {
            LOG.warn("No type mapping found for incoming message {}", innerMsg);
            return;
        }
        IncomingRequestTypeParameters requestParams;
        IncomingResponseTypeParameters responseParams;

        if ((requestParams = typeParameters.getIncomingRequestTypeParameters()) != null) {
            if (outgoingRequestStates.containsKey(nonce)) {
                LOG.debug("Request to self received, ignoring: {}", innerMsg);
                return;
            }

            if (incomingRequestStates.containsKey(nonce)) {
                LOG.debug("Duplicate request received, ignoring: {}", innerMsg);
                return;
            }

            Endpoint srcEndpoint = endpointDirectory.lookup(address);
            IncomingRequestState<N> incomingState = new IncomingRequestState<>(requestParams, srcEndpoint, nonce, innerMsg);
            incomingRequestStates.put(nonce, incomingState);

            Duration discardDuration = incomingState.getDiscardDuration();
            Object discardEvent = incomingState.getDiscardEvent();
            endpointScheduler.scheduleMessage(discardDuration, selfEndpoint, selfEndpoint, discardEvent);

            // wrap source endpoint so they can derive address
            TransmissionInputEndpoint<A> proxyEndpoint = new TransmissionInputEndpoint<>(selfEndpoint, address);
            userEndpoint.send(proxyEndpoint, innerMsg);
        } else if ((responseParams = typeParameters.getIncomingResponseTypeParameters()) != null) {
            if (incomingResponseStates.containsKey(nonce)) {
                LOG.debug("Duplicate response received, ignoring: {}", innerMsg);
                return;
            }

            if (!outgoingRequestStates.containsKey(nonce)) {
                LOG.debug("Response does not have a matching request, ignoring: {}", innerMsg);
                return;
            }

            Endpoint dstEndpoint = endpointDirectory.lookup(address);
            IncomingResponseState<N> incomingState = new IncomingResponseState<>(responseParams, dstEndpoint, nonce, innerMsg);
            incomingResponseStates.put(nonce, incomingState);

            Duration discardDuration = incomingState.getDiscardDuration();
            Object discardEvent = incomingState.getDiscardEvent();
            endpointScheduler.scheduleMessage(discardDuration, selfEndpoint, selfEndpoint, discardEvent);
            
            // wrap source endpoint so they can derive address
            TransmissionInputEndpoint<A> proxyEndpoint = new TransmissionInputEndpoint<>(selfEndpoint, address);
            userEndpoint.send(proxyEndpoint, innerMsg);
        } else {
            LOG.warn("Unsupported incoming message {}", innerMsg);
        }
    }
}