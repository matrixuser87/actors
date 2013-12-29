/*
 * Copyright (c) 2013, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.peernetic.rpc.transport.transports.test;

import com.offbynull.peernetic.rpc.transport.Transport;
import com.offbynull.peernetic.rpc.transport.TransportFactory;
import java.io.IOException;
import org.apache.commons.lang3.Validate;

/**
 * Creates a {@link TestTransportFactory} based on properties in this class.
 * @param <A> address type
 * @author Kasra Faghihi
 */
public final class TestTransportFactory<A> implements TransportFactory<A> {

    private TestHub<A> hub;
    private long timeout = 10000;
    private A address;

    /**
     * Constructs a {@link TestTransportFactory} object.
     * @param hub hub
     * @param address address
     * @throws NullPointerException if any arguments are {@code null}
     */
    public TestTransportFactory(TestHub<A> hub, A address) {
        Validate.notNull(hub);
        Validate.notNull(address);
        
        this.hub = hub;
        this.address = address;
    }

    /**
     * Gets the hub.
     * @return hub
     */
    public TestHub<A> getHub() {
        return hub;
    }

    /**
     * Sets the hub.
     * @param hub hub
     * @throws NullPointerException if any arguments are {@code null}
     */
    public void setHub(TestHub<A> hub) {
        Validate.notNull(hub);
        this.hub = hub;
    }

    /**
     * Gets the timeout.
     * @return timeout
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout.
     * @param timeout timeout
     * @throws IllegalArgumentException if {@code timeout <= 0}
     */
    public void setTimeout(long timeout) {
        Validate.inclusiveBetween(1L, Long.MAX_VALUE, timeout);
        this.timeout = timeout;
    }

    /**
     * Gets the address.
     * @return address
     */
    public A getAddress() {
        return address;
    }

    /**
     * Sets the address.
     * @param address address
     * @throws NullPointerException if any arguments are {@code null}
     */
    public void setAddress(A address) {
        Validate.notNull(address);
        this.address = address;
    }
    
    @Override
    public Transport<A> createTransport() throws IOException {
        return new TestTransport<>(address, hub, timeout);
    }
    
}