/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
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
package com.offbynull.actors.core.gateway.web;

import static com.offbynull.actors.core.common.DefaultAddresses.DEFAULT_WEB;
import com.offbynull.actors.core.gateway.Gateway;
import com.offbynull.actors.core.shuttle.Shuttle;
import com.offbynull.actors.core.shuttles.simple.Bus;
import com.offbynull.actors.core.shuttles.test.NullShuttle;
import org.apache.commons.lang3.Validate;

/**
 * {@link Gateway} that allows you read and write messages using via a servlet.
 * @author Kasra Faghihi
 */
public final class WebGateway implements Gateway {

    private final Thread thread;
    private final Shuttle inShuttle;
    private final Bus bus;

    /**
     * Create a {@link WebGateway} instance. Equivalent to calling {@code create(DefaultAddresses.DEFAULT_WEB)}.
     * @return new direct gateway
     */
    public static WebGateway create() {
        return create(DEFAULT_WEB);
    }

    /**
     * Create a {@link WebGateway} instance.
     * @param prefix address prefix for this gateway
     * @return new web gateway
     * @throws NullPointerException if any argument is {@code null}
     */
    public static WebGateway create(String prefix) {
        WebGateway gateway = new WebGateway(prefix);
        gateway.thread.start();
        return gateway;
    }
    
    private WebGateway(String prefix) {
        Validate.notNull(prefix);

        bus = new Bus();
        inShuttle = new NullShuttle(prefix);
        thread = new Thread(new WebRunnable(prefix, bus));
        thread.setDaemon(true);
        thread.setName(getClass().getSimpleName() + "-" + prefix);
    }

    @Override
    public Shuttle getIncomingShuttle() {
        return inShuttle;
    }

    @Override
    public void addOutgoingShuttle(Shuttle shuttle) {
        Validate.notNull(shuttle);
        bus.add(new AddShuttle(shuttle));
    }

    @Override
    public void removeOutgoingShuttle(String shuttlePrefix) {
        Validate.notNull(shuttlePrefix);
        bus.add(new RemoveShuttle(shuttlePrefix));
    }

    @Override
    public void close() throws InterruptedException {
        thread.interrupt();
        thread.join();
    }
}