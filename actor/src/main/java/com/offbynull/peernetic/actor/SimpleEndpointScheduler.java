package com.offbynull.peernetic.actor;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Validate;

public final class SimpleEndpointScheduler implements EndpointScheduler {
    private ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    
    @Override
    public void scheduleMessage(Duration delay, Endpoint source, Endpoint destination, Object message) {
        Validate.notNull(delay);
        Validate.notNull(source);
        Validate.notNull(destination);
        Validate.notNull(message);
        scheduler.schedule(() -> { destination.send(source, message); }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws IOException {
        scheduler.shutdownNow();
    }
}
