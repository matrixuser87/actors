package com.offbynull.peernetic.core.gateways.recorder;

import com.offbynull.peernetic.core.Shuttle;
import com.offbynull.peernetic.core.common.AddressUtils;
import com.offbynull.peernetic.core.common.Serializer;
import com.offbynull.peernetic.core.gateway.Gateway;
import java.io.File;
import org.apache.commons.lang3.Validate;

public final class ReplayerGateway implements Gateway {

    private final Thread readThread;
    
    public static ReplayerGateway replay(Shuttle dstShuttle, String dstAddress, File file, Serializer serializer) {
        Validate.notNull(dstShuttle);
        Validate.notNull(dstAddress);
        Validate.notNull(file);
        Validate.notNull(serializer);
        Validate.isTrue(AddressUtils.isParent(dstShuttle.getPrefix(), dstAddress));
        
        ReadRunnable readRunnable = new ReadRunnable(dstShuttle, dstAddress, file, serializer);
        Thread readThread = new Thread(readRunnable);
        readThread.setDaemon(true);
        readThread.setName(RecorderGateway.class.getSimpleName());
        
        ReplayerGateway ret = new ReplayerGateway(readThread);
        
        readThread.start();        
        
        return ret;
    }

    private ReplayerGateway(Thread readThread) {
        this.readThread = readThread;
    }

    @Override
    public void close() throws Exception {
        readThread.interrupt();
    }

    
}
