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
package com.offbynull.peernetic.core.actor.helpers;

import com.offbynull.coroutines.user.CoroutineRunner;
import com.offbynull.peernetic.core.actor.Context;
import com.offbynull.peernetic.core.shuttle.Address;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.Validate;

/**
 * Forwards incoming messages to different {@link Subcoroutine}s based on the destination address. Each subcoroutine is invoked within its
 * own isolated {@link CoroutineRunner}. Use {@link #getController() } to add or remove subcoroutines from this router.
 * <p>
 * For example, imagine that this router belongs to an actor with the address "actor:0". To initialize a router under the address suffix
 * "router" ...
 * <pre>
 * SubcoroutineRouter router = new SubcoroutineRouter(Address.of("router"));
 * </pre>
 * To add a subcoroutine to that router ...
 * <pre>
 * Subcoroutine mySubcoroutine = ...; // this subcoroutine's relative address is "router:mysub"
 * router.getController().add(mySubcoroutine, AddBehaviour.ADD_PRIME); // primes mySubcoroutine with the current incoming message
 * </pre>
 * To remove the subcoroutine that was added above ...
 * <pre>
 * router.getController().remove(mySubcoroutine.getAddress());
 * </pre>
 * <p>
 * When using this class within an actor, make sure to construct it within the actor when the actor is executing. This class is not
 * immutable or thread-safe.
 * @author Kasra Faghihi
 */
public final class SubcoroutineRouter {

    private final Address address;
    private final Context context;
    private final Map<String, RouterEntry> idMap;
    private final Controller controller;

    /**
     * Constructs a {@link SubcoroutineRouter}.
     * @param address relative address of this router (relative to the calling actor's self address)
     * @param context actor context
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code address} is empty
     */
    public SubcoroutineRouter(Address address, Context context) {
        Validate.notNull(address);
        Validate.notNull(context);
        Validate.isTrue(!address.isEmpty());
        this.address = address;
        this.context = context;
        idMap = new HashMap<>();
        controller = new Controller();
    }

    /**
     * Get the controller for this router. The controller allows you to add subcoroutines to and remove subcoroutines from this router. It
     * is safe to use the returned controller to add/remove subcoroutines from within a subcoroutines invoked by this subcoroutine router.
     * @return controller for this router
     */
    public Controller getController() {
        return controller;
    }

    /**
     * Forward the current incoming message to the appropriate subcoroutine. If address of the incoming message doesn't map to a
     * subcoroutine assigned to this router, this method will return without forwarding the incoming message.
     * @return a {@link ForwardResult} object that provides information on what this call to forward did (if it was forwarded, if the
     * subcoroutine it got forwarded to completed, etc..)
     * @throws Exception if the subcoroutine that the incoming message was forwarded to threw an exception
     */
    public ForwardResult forward() throws Exception {
        String key;
        try {
            Address relativeDestinationAddress = context.getDestination().removePrefix(context.getSelf());
            Validate.isTrue(address.isPrefixOf(relativeDestinationAddress));
            
            Address subId = relativeDestinationAddress.removePrefix(address);
            Validate.isTrue(!subId.isEmpty());
            
            key = subId.getElement(0);
        } catch (IllegalArgumentException iae) {
            return new ForwardResult(null, null, false);
        }

        RouterEntry container = idMap.get(key);

        if (container != null) {
            boolean completed = !container.step();
            Object result = null;
            if (completed) {
                idMap.remove(key);
                result = container.getResult();
            }
            
            return new ForwardResult(container.getSubcoroutine(), result, completed);
        } else {
            return new ForwardResult(null, null, false);
        }
    }

    /**
     * Get the address of this router. Incoming messages destined for this address should trigger this router to run. The address returned
     * by this method must be relative to the calling actor's self address (relative to {@link Context#getSelf()}).
     * @return absolute relative address of this router
     */
    public Address getAddress() {
        return address;
    }
    
    /**
     * Holds results of call to {@link SubcoroutineRouter#forward() }.
     */
    public static final class ForwardResult {
        private final Subcoroutine<?> subcoroutine;
        private final Object result;
        private final boolean completed;

        private ForwardResult(Subcoroutine<?> subcoroutine, Object result, boolean completed) {
            if (result != null || completed) {
                Validate.validState(subcoroutine != null);
            }
            this.subcoroutine = subcoroutine;
            this.result = result;
            this.completed = completed;
        }

        /**
         * Returns {@code true} if the incoming message routed to a subcoroutine, or {@code false} if no subcoroutine existed at the
         * destination address.
         * @return {@code true} if the incoming message routed to a subcoroutine
         */
        public boolean isForwarded() {
            return subcoroutine != null;
        }

        /**
         * Returns {@code true} if the subcoroutine routed to completed.
         * @return {@code true} if the subcoroutine routed to completed
         * @throws IllegalArgumentException if {@link #isForwarded() } returns {@code false}
         */
        public boolean isCompleted() {
            Validate.isTrue(subcoroutine != null);
            return completed;
        }

        /**
         * Gets the subcoroutine that was executed by the incoming message.
         * @return subcoroutine that was executed by the incoming message
         * @throws IllegalArgumentException if {@link #isForwarded() } returns {@code false}
         */
        public Subcoroutine<?> getSubcoroutine() {
            Validate.isTrue(subcoroutine != null);
            return subcoroutine;
        }

        /**
         * Gets the result of the subcoroutine that was executed and completed by the incoming message.
         * @return result of the compelted subcoroutine that was executed by the incoming message
         * @throws IllegalArgumentException if {@link #isForwarded() } returns {@code false}, or if {@link #isCompleted() } returns
         * {@code false}
         */
        public Object getResult() {
            Validate.isTrue(subcoroutine != null);
            Validate.isTrue(completed);
            return result;
        }
    }
    
    private static final class RouterEntry {
        private final Subcoroutine<?> subcoroutine;
        private final SubcoroutineStepper<?> subcoroutineStepper;

        public RouterEntry(Context context, Subcoroutine<?> subcoroutine) {
            Validate.notNull(subcoroutine);
            this.subcoroutine = subcoroutine;
            this.subcoroutineStepper = new SubcoroutineStepper<>(context, subcoroutine);
        }

        public Subcoroutine<?> getSubcoroutine() {
            return subcoroutine;
        }

        public boolean step() {
            return subcoroutineStepper.step();
        }

        public Object getResult() {
            return subcoroutineStepper.getResult();
        }
    }
    
    /**
     * Controller to add subcoroutines to and remove subcoroutines from the router that this controller is assigned to. It is safe to use
     * this class to add/remove subcoroutines from within a subcoroutine invoked by the router that owns this controller.
     */
    public final class Controller {
        /**
         * Add a subcoroutine to the router that owns this controller. If you choose to prime the subcoroutine that's being
         * added (by choosing either {@link AddBehaviour#ADD_PRIME} or {@link AddBehaviour#ADD_PRIME_NO_FINISH} for {@code addBehaviour}),
         * this method will forward the current incoming message to it.
         * @param subcoroutine subcoroutine to add
         * @param addBehaviour add behaviour
         * @throws NullPointerException if any argument is {@code null}, or if {@code subcoroutine.getAddress()} returns {@code null}
         * @throws IllegalArgumentException if another subcoroutine with {@code subcoroutine}'s address has already assigned to the owning
         * router, or if the {@code subcoroutine}'s address isn't a <b>direct</b> child of the owning router
         * @throws IllegalStateException if {@code addBehaviour} was set to {@link AddBehaviour#ADD_PRIME_NO_FINISH}, but
         * {@code subcoroutine} finished after priming
         * @throws Exception if {@code subcoroutine} threw an exception while priming ({@code addBehaviour} has to be set to either
         * {@link AddBehaviour#ADD_PRIME} or {@link AddBehaviour#ADD_PRIME_NO_FINISH} for this to be possible)
         */
        public void add(Subcoroutine<?> subcoroutine, AddBehaviour addBehaviour) throws Exception {
            Validate.notNull(subcoroutine);
            Validate.notNull(addBehaviour);

            Address subcoroutineId = subcoroutine.getAddress();
            Validate.notNull(subcoroutineId);
            Address suffix = subcoroutineId.removePrefix(address);
            Validate.isTrue(suffix.size() == 1);
            
            String key = suffix.getElement(0);

            RouterEntry container = new RouterEntry(context, subcoroutine);
            RouterEntry existingContainer = idMap.putIfAbsent(key, container);
            Validate.isTrue(existingContainer == null);

            switch (addBehaviour) {
                case ADD:
                    break;
                case ADD_PRIME:
                    forceForward(key, false);
                    break;
                case ADD_PRIME_NO_FINISH:
                    forceForward(key, true);
                    break;
                default:
                    throw new IllegalStateException(); // should never happen
            }
        }
        
        /**
         * Removes a subcoroutine from the router that owns this controller.
         * @param address address of subcoroutine to remove (relative to actor)
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if a subcoroutine with this address is not assigned to the owning router, or if {@code address}
         * isn't a <b>direct</b> child of the owning router
         */
        public void remove(Address address) {
            Validate.notNull(address);
            
            Address suffix = address.removePrefix(SubcoroutineRouter.this.address);
            Validate.isTrue(suffix.size() == 1);
            
            String key = suffix.getElement(0);
            
            RouterEntry old = idMap.remove(key);
            Validate.isTrue(old != null);
        }
        
        /**
         * Get the number of subcoroutines assigned to the router that owns this controller.
         * @return number of subcoroutines
         */
        public int size() {
            return idMap.size();
        }

        /**
         * Get the address of the router that owns this controller. The address returned by this method must be relative to the calling
         * actor's self address (relative to {@link Context#getSelf()}).
         * @return relative address of router
         */
        public Address getAddress() {
            return SubcoroutineRouter.this.address;
        }
        
        private boolean forceForward(String id, boolean mustNotFinish) throws Exception {
            Validate.notNull(id);

            RouterEntry entry = idMap.get(id);

            boolean forwarded = false;
            if (entry != null) {
                boolean running = entry.step();
                if (!running) {
                    Validate.validState(!mustNotFinish, "Entry pointed to by suffix was not supposed to finish");
                    idMap.remove(id);
                }
                forwarded = true;
            }

            return forwarded;
        }
    }
    
    /**
     * Behaviour when adding a subcoroutine via {@link Controller#add(com.offbynull.peernetic.core.actor.helpers.Subcoroutine,
     * com.offbynull.peernetic.core.actor.helpers.SubcoroutineRouter.AddBehaviour) }.
     */
    public enum AddBehaviour {
        /**
         * Add the subcoroutine to the router.
         */
        ADD,
        /**
         * Add the subcoroutine to the router, and prime it with the current incoming message.
         */
        ADD_PRIME,
        /**
         * Add the subcoroutine to router, prime it with the current incoming message, and make sure the subcoroutine is still running after
         * priming.
         */
        ADD_PRIME_NO_FINISH;
    }
}
