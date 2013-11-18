package com.offbynull.p2prpc.common.filters;

import com.offbynull.p2prpc.transport.IncomingMessage;
import com.offbynull.p2prpc.transport.IncomingMessageListener;
import com.offbynull.p2prpc.transport.IncomingMessageResponseHandler;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.Validate;

public final class BlacklistIncomingMessageListener<A> implements IncomingMessageListener<A> {
    private Set<A> disallowedSet;

    public BlacklistIncomingMessageListener(Set<A> disallowedSet) {
        Validate.noNullElements(disallowedSet);
        
        this.disallowedSet = Collections.newSetFromMap(new ConcurrentHashMap<A, Boolean>());
        this.disallowedSet.addAll(disallowedSet);
    }

    public void addAddress(A e) {
        disallowedSet.add(e);
    }

    public void removeAddress(A e) {
        disallowedSet.remove(e);
    }

    public void addAddresses(Collection<? extends A> c) {
        disallowedSet.addAll(c);
    }

    public void removeAddresses(Collection<? extends A> c) {
        disallowedSet.removeAll(c);
    }
    
    @Override
    public void messageArrived(IncomingMessage<A> message, IncomingMessageResponseHandler responseCallback) {
        A from = message.getFrom();
        
        if (disallowedSet.contains(from)) {
            throw new AddressInBlacklistException();
        }
    }
    
    public static class AddressInBlacklistException extends RuntimeException {
    }
}