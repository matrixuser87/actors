package com.offbynull.peernetic.core.gateways.timer;

import com.offbynull.peernetic.core.shuttle.Shuttle;
import com.offbynull.peernetic.core.gateway.InputGateway;
import com.offbynull.peernetic.core.gateway.OutputGateway;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

public final class TimerGateway implements InputGateway, OutputGateway {

    private final ScheduledExecutorService service;
    private final ConcurrentHashMap<String, Shuttle> outgoingShuttles;
    
    private final InternalShuttle shuttle;

    public TimerGateway(String prefix) {
        Validate.notNull(prefix);

        ThreadFactory threadFactory = new BasicThreadFactory.Builder().daemon(true).namingPattern("TimerGateway-" + prefix).build();
        service = Executors.newSingleThreadScheduledExecutor(threadFactory);
        outgoingShuttles = new ConcurrentHashMap<>();
        
        shuttle = new InternalShuttle(prefix, service, outgoingShuttles);
    }

    @Override
    public Shuttle getIncomingShuttle() {
        return shuttle;
    }

    @Override
    public void addOutgoingShuttle(Shuttle shuttle) {
        Validate.notNull(shuttle);
        Shuttle oldShuttle = outgoingShuttles.putIfAbsent(shuttle.getPrefix(), shuttle);
        Validate.isTrue(oldShuttle == null);
    }

    @Override
    public void removeOutgoingShuttle(String shuttlePrefix) {
        Validate.notNull(shuttlePrefix);
        Shuttle oldShuttle = outgoingShuttles.remove(shuttlePrefix);
        Validate.isTrue(oldShuttle == null);
    }

    @Override
    public void close() throws Exception {
        service.shutdownNow();
    }
}
