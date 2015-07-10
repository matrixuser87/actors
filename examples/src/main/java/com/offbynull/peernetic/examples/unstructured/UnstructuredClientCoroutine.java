package com.offbynull.peernetic.examples.unstructured;

import com.offbynull.peernetic.examples.unstructured.internalmessages.Start;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.peernetic.core.actor.Context;
import com.offbynull.peernetic.core.actor.helpers.SubcoroutineRouter;
import com.offbynull.peernetic.core.actor.helpers.SubcoroutineRouter.AddBehaviour;
import com.offbynull.peernetic.core.actor.helpers.SubcoroutineRouter.Controller;
import static com.offbynull.peernetic.core.gateways.log.LogMessage.debug;
import static com.offbynull.peernetic.core.gateways.log.LogMessage.info;
import com.offbynull.peernetic.core.shuttle.Address;
import com.offbynull.peernetic.examples.common.AddressTransformer;
import com.offbynull.peernetic.visualizer.gateways.graph.AddNode;
import com.offbynull.peernetic.visualizer.gateways.graph.MoveNode;
import java.util.Random;
import org.apache.commons.collections4.set.UnmodifiableSet;

public final class UnstructuredClientCoroutine implements Coroutine {

    @Override
    public void run(Continuation cnt) throws Exception {
        Context ctx = (Context) cnt.getContext();

        Start start = ctx.getIncomingMessage();
        Address timerAddress = start.getTimerPrefix();
        Address graphAddress = start.getGraphAddress();
        Address logAddress = start.getLogAddress();
        UnmodifiableSet<String> bootstrapLinks = start.getBootstrapLinks();
        AddressTransformer addressTransformer = start.getAddressTransformer();
        long seed = start.getSeed();

        Random random = new Random(seed);

        State state = new State(seed, 3, 4, 256, bootstrapLinks, addressTransformer);

        ctx.addOutgoingMessage(logAddress, info("Starting client with seed {} and bootstrap {}", seed, bootstrapLinks));
        ctx.addOutgoingMessage(graphAddress, new AddNode(addressTransformer.selfAddressToLinkId(ctx.getSelf())));
        ctx.addOutgoingMessage(graphAddress,
                new MoveNode(
                        addressTransformer.selfAddressToLinkId(ctx.getSelf()),
                        random.nextInt(1400),
                        random.nextInt(1400)
                )
        );

        Address routerId = Address.of("router");
        SubcoroutineRouter outgoingLinkRouter = new SubcoroutineRouter(routerId, ctx);
        Controller controller = outgoingLinkRouter.getController();
        
        // start subcoroutine to deal with incoming requests
        ctx.addOutgoingMessage(logAddress, debug("Adding handler"));
        controller.add(
                new IncomingMessageHandlerSubcoroutine(
                        routerId.appendSuffix("handler"),
                        timerAddress,
                        logAddress,
                        state,
                        controller),
                AddBehaviour.ADD_PRIME_NO_FINISH);
        
        // start subcoroutine to populate address cache
        ctx.addOutgoingMessage(logAddress, debug("Adding querier"));
        controller.add(
                new OutgoingQuerySubcoroutine(
                        routerId.appendSuffix("querier"),
                        timerAddress,
                        logAddress,
                        state),
                AddBehaviour.ADD_PRIME_NO_FINISH);
        
        // start subcoroutines to make and maintain outgoing connections
        for (int i = 0; i < state.getMaxOutgoingLinks(); i++) {
            ctx.addOutgoingMessage(logAddress, debug("Adding outgoing link maintainer {}", i));
            controller.add(
                    new OutgoingLinkSubcoroutine(
                            routerId.appendSuffix("out" + i),
                            graphAddress,
                            timerAddress,
                            logAddress,
                            state),
                    AddBehaviour.ADD_PRIME_NO_FINISH);
        }

        while (true) {
            cnt.suspend();
            outgoingLinkRouter.forward();
        }
    }
}
