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
package com.offbynull.peernetic.rpc;

import com.offbynull.peernetic.rpc.invoke.AsyncCapturer;
import com.offbynull.peernetic.rpc.invoke.AsyncCapturerFactory;
import com.offbynull.peernetic.rpc.invoke.AsyncCapturerHandler;
import com.offbynull.peernetic.rpc.invoke.AsyncCapturerHandlerCallback;
import com.offbynull.peernetic.rpc.invoke.Capturer;
import com.offbynull.peernetic.rpc.invoke.CapturerFactory;
import com.offbynull.peernetic.rpc.invoke.CapturerHandler;
import com.offbynull.peernetic.rpc.transport.OutgoingMessageResponseListener;
import com.offbynull.peernetic.rpc.transport.Transport;
import com.offbynull.peernetic.rpc.transport.TransportUtils;
import java.nio.ByteBuffer;
import org.apache.commons.lang3.Validate;

final class ServiceAccessor<A> {
    private Transport<A> transport;
    private CapturerFactory capturerFactory;
    private AsyncCapturerFactory asyncCapturerFactory;

    public ServiceAccessor(Transport<A> transport, CapturerFactory capturerFactory, AsyncCapturerFactory asyncCapturerFactory) {
        Validate.notNull(transport);
        Validate.notNull(capturerFactory);
        Validate.notNull(asyncCapturerFactory);
        
        this.transport = transport;
        this.capturerFactory = capturerFactory;
        this.asyncCapturerFactory = asyncCapturerFactory;
    }
    
    public <T> T accessService(final A address, final int serviceId, Class<T> type, final RuntimeException throwOnCommFailure,
            final RuntimeException throwOnInvokeFailure) {
        Validate.notNull(address);
        Validate.notNull(type);
        Validate.notNull(throwOnCommFailure);
        Validate.notNull(throwOnInvokeFailure);
        
        
        Capturer<T> capturer = capturerFactory.createCapturer(type);
        T obj = capturer.createInstance(new CapturerHandler() {

            @Override
            public byte[] invocationTriggered(byte[] data) {
                try {
                    ByteBuffer buffer = ByteBuffer.allocate(data.length + 4);
                    buffer.putInt(serviceId);
                    buffer.put(data);
                    buffer.position(0);
                    
                    ByteBuffer resp = TransportUtils.sendAndWait(transport, address, buffer);
                    byte[] respArray = new byte[resp.remaining()];
                    resp.get(respArray);
                    
                    return respArray;
                } catch (InterruptedException ie) {
                    Thread.interrupted(); // ignore interrupt, if it's interrupted
                    throw throwOnCommFailure;
                } catch (RuntimeException re) {
                    throw throwOnCommFailure;
                }
            }

            @Override
            public void invocationFailed(Throwable err) {
                throw throwOnInvokeFailure;
            }
        });

        return obj;
    }

    public <T, AT> AT accessServiceAsync(final A address, final int serviceId, Class<T> type, Class<AT> asyncType,
            final RuntimeException throwOnCommFailure, final RuntimeException throwOnInvokeFailure) {
        Validate.notNull(address);
        Validate.notNull(type);
        Validate.notNull(asyncType);
        Validate.notNull(throwOnCommFailure);
        Validate.notNull(throwOnInvokeFailure);
        
        
        AsyncCapturer<T, AT> capturer = asyncCapturerFactory.createAsyncCapturer(type, asyncType);
        AT obj = capturer.createInstance(new AsyncCapturerHandler() {

            @Override
            public void invocationFailed(Throwable err) {
                throw throwOnInvokeFailure;
            }

            @Override
            public void invocationTriggered(byte[] data, final AsyncCapturerHandlerCallback responseHandler) {
                try {
                    ByteBuffer buffer = ByteBuffer.allocate(data.length + 4);
                    buffer.putInt(serviceId);
                    buffer.put(data);
                    buffer.position(0);
                    
                    transport.sendMessage(address, buffer, new OutgoingMessageResponseListener() {

                        @Override
                        public void responseArrived(ByteBuffer resp) {
                            byte[] respArray = new byte[resp.remaining()];
                            resp.get(respArray);
                            
                            responseHandler.responseArrived(respArray);
                        }

                        @Override
                        public void errorOccurred(Object error) {
                            responseHandler.responseFailed(throwOnCommFailure);
                        }
                    });
                } catch (RuntimeException ex) {
                    responseHandler.responseFailed(throwOnInvokeFailure);
                }
            }
        });

        return obj;
    }
}
