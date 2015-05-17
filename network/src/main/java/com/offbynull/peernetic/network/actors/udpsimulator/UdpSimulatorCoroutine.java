/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
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
package com.offbynull.peernetic.network.actors.udpsimulator;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.peernetic.core.actor.Actor;
import com.offbynull.peernetic.core.actor.ActorThread;
import com.offbynull.peernetic.core.actor.helpers.ProxyHelper;
import com.offbynull.peernetic.core.actor.helpers.ProxyHelper.ForwardInformation;
import com.offbynull.peernetic.core.actor.Context;
import com.offbynull.peernetic.core.shuttle.Address;
import com.offbynull.peernetic.network.gateways.udp.UdpGateway;
import java.time.Instant;

/**
 * {@link Coroutine} actor that attempts to simulate a {@link UdpGateway}.
 * <p>
 * In the following example, there are two {@link Actor}s: {@code sender} and {@code echoer}. {@code sender} sends 10 message and waits for
 * those messages to be echoed back to it, while {@code echoer} echoes back whatever is sent to it. Both of these actors are assigned
 * their own {@link UdpSimulatorCoroutine} (both called {@code proxy} -- note that each actor is running in its own {@link ActorThread} so
 * there is no naming conflict here), and pass messages through it to the other to simulate communicating over UDP.
 * <p>
 * Note that UDP is unreliable. Each message is sent as a single packet, and packets may come out of order or not at all. This example
 * simulates mild unreliability in terms of packet loss and packet duplication, and heavy unreliability in terms of out-of-order packet
 * arrival (jitter is set to a maximum of 1 second). The line can be modified to change unreliability parameters.
 * <pre>
 * Coroutine sender = (cnt) -&gt; {
 *     Context ctx = (Context) cnt.getContext();
 *     Address dstAddr = ctx.getIncomingMessage();
 *
 *     // Send out 10 messages
 *     for (int i = 0; i &lt; 10; i++) {
 *         ctx.addOutgoingMessage(Address.of("hi"), dstAddr, i);
 *     }
 *     
 *     // Print out messages as they come in
 *     while (true) {
 *         cnt.suspend();
 *         System.out.println(ctx.getIncomingMessage().toString()); // Shouldn't be doing I/O in actor, but this example so its okay
 *     }
 * };
 *
 * Coroutine echoer = (cnt) -&gt; {
 *     Context ctx = (Context) cnt.getContext();
 *
 *     // Echo back anything that comes in
 *     while (true) {
 *         Address src = ctx.getSource();
 *         Object msg = ctx.getIncomingMessage();
 *         ctx.addOutgoingMessage(src, msg);
 *         cnt.suspend();
 *     }
 * };
 *
 * 
 * 
 * // Create a timer gateway. Used by UdpSimualtorCoroutine to time message arrivals
 * TimerGateway timerGateway = new TimerGateway("timer");
 * 
 * // Create threads for echoer and sender
 * ActorThread echoerThread = ActorThread.create("echoer");
 * ActorThread senderThread = ActorThread.create("sender");
 * 
 * // Bind shuttles for echoer
 * echoerThread.addOutgoingShuttle(senderThread.getIncomingShuttle());
 * echoerThread.addOutgoingShuttle(timerGateway.getIncomingShuttle());
 * 
 * // Bind shuttles for sender
 * senderThread.addOutgoingShuttle(echoerThread.getIncomingShuttle());
 * senderThread.addOutgoingShuttle(timerGateway.getIncomingShuttle());
 * 
 * // Bind shuttles for timer gateway
 * timerGateway.addOutgoingShuttle(senderThread.getIncomingShuttle());
 * timerGateway.addOutgoingShuttle(echoerThread.getIncomingShuttle());
 *
 * // Create echoer actor + the udp simulator that proxies it
 * echoerThread.addCoroutineActor("echoer", echoer);
 * echoerThread.addCoroutineActor("proxy", new UdpSimulatorCoroutine(), // Create UDP simulator proxy and prime
 *         new StartUdpSimulator(
 *                 Address.of("timer"),                                                                      // Timer to use to delay msgs
 *                 Address.fromString("echoer:echoer"),                                                      // Actor being proxied
 *                 () -&gt;  new SimpleLine(                                                                    // Unreliability params
 *                                0L,
 *                                Duration.ofSeconds(1L),
 *                                Duration.ofSeconds(1L),
 *                                0.1,
 *                                0.1,
 *                                10,
 *                                1500,
 *                                new SimpleSerializer())));
 * 
 * // Create sender actor + the udp simulator that proxies it
 * senderThread.addCoroutineActor("sender", sender, Address.fromString("sender:proxy:echoer:echoer"));
 * senderThread.addCoroutineActor("proxy", new UdpSimulatorCoroutine(), // Create UDP simulator proxy and prime
 *         new StartUdpSimulator(
 *                 Address.of("timer"),                                                                      // Timer to use to delay msgs
 *                 Address.fromString("sender:sender"),                                                      // Actor being proxied
 *                 () -&gt; new SimpleLine(                                                                    // Unreliability params
 *                                 0L,
 *                                 Duration.ofSeconds(1L),
 *                                 Duration.ofSeconds(1L),
 *                                 0.1,
 *                                 0.1,
 *                                 10,
 *                                 1500,
 *                                 new SimpleSerializer())));
 * 
 * // Sleep for 10 seconds so you can see the output in console
 * Thread.sleep(10000L);
 * </pre>
 * @author Kasra Faghihi
 */
public final class UdpSimulatorCoroutine implements Coroutine {

    @Override
    public void run(Continuation cont) throws Exception {
        Context ctx = (Context) cont.getContext();

        StartUdpSimulator startMsg = ctx.getIncomingMessage();

        Line line = startMsg.getLineFactory().get();
        Address timerPrefix = startMsg.getTimerPrefix();
        Address actorPrefix = startMsg.getActorPrefix();
        
        ProxyHelper proxyHelper = new ProxyHelper(ctx, actorPrefix);

        while (true) {
            cont.suspend();
            Object msg = ctx.getIncomingMessage();
            Instant time = ctx.getTime();
            
            if (proxyHelper.isMessageFrom(timerPrefix)) {
                // Timer message indicating that a message is suppose to go out now
                TransitMessage tm = (TransitMessage) msg;
                ctx.addOutgoingMessage(
                        tm.getSourceId(),
                        tm.getDestinationAddress(),
                        tm.getMessage());
            } else if (proxyHelper.isMessageFromActor()) {
                // Outgoing message
                ForwardInformation forwardInfo = proxyHelper.generateOutboundForwardInformation();
                DepartMessage dm = new DepartMessage(msg,
                        forwardInfo.getProxyFromId(),
                        forwardInfo.getProxyToAddress());
                for (TransitMessage tm : line.processOutgoing(time, dm)) {
                    ctx.addOutgoingMessage(timerPrefix.appendSuffix("" + tm.getDuration().toMillis()), tm);
                }
            } else {
                // Incoming message
                ForwardInformation forwardInfo = proxyHelper.generatInboundForwardInformation();
                DepartMessage dm = new DepartMessage(msg,
                        forwardInfo.getProxyFromId(),
                        forwardInfo.getProxyToAddress());
                for (TransitMessage tm : line.processIncoming(time, dm)) {
                    ctx.addOutgoingMessage(timerPrefix.appendSuffix("" + tm.getDuration().toMillis()), tm);
                }
            }
        }
    }
}
