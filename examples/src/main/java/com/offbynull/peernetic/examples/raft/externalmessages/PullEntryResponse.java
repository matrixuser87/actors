package com.offbynull.peernetic.examples.raft.externalmessages;

import java.io.Serializable;
import org.apache.commons.lang3.Validate;

public final class PullEntryResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final Object value;
    private final int index;
    private final int term;

    public PullEntryResponse(Object value, int index, int term) {
        Validate.notNull(value);
        Validate.isTrue(index >= 0);
        this.value = value;
        this.index = index;
        this.term = term;
    }

    public Object getValue() {
        return value;
    }

    public int getIndex() {
        return index;
    }

    public int getTerm() {
        return term;
    }

}