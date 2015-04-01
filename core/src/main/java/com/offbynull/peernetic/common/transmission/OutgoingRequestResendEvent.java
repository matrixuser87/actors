package com.offbynull.peernetic.common.transmission;

import com.offbynull.peernetic.common.message.Nonce;
import org.apache.commons.lang3.Validate;

final class OutgoingRequestResendEvent<N> {
    private final Nonce<N> nonce;

    public OutgoingRequestResendEvent(Nonce<N> nonce) {
        Validate.notNull(nonce);
        this.nonce = nonce;
    }

    public Nonce<N> getNonce() {
        return nonce;
    }
    
}