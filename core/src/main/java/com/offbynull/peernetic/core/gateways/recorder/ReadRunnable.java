package com.offbynull.peernetic.core.gateways.recorder;

import com.offbynull.peernetic.core.shuttle.Message;
import com.offbynull.peernetic.core.shuttle.Shuttle;
import com.offbynull.peernetic.core.shuttle.AddressUtils;
import com.offbynull.peernetic.core.common.Serializer;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ReadRunnable implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ReadRunnable.class);

    private final Shuttle dstShuttle;
    private final File file;
    private final String dstAddress;
    private final Serializer serializer;

    ReadRunnable(Shuttle dstShuttle, String dstAddress, File file, Serializer serializer) {
        Validate.notNull(dstShuttle);
        Validate.notNull(file);
        Validate.notNull(serializer);
        Validate.isTrue(AddressUtils.isPrefix(dstShuttle.getPrefix(), dstAddress));
        this.dstShuttle = dstShuttle;
        this.file = file;
        this.dstAddress = dstAddress;
        this.serializer = serializer;
    }

    @Override
    public void run() {
        LOG.info("Started reading");
        try (FileInputStream fis = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(fis)) {
            Instant lastTime = null;
            while (true) {
                int size = dis.readInt();
                byte[] data = new byte[size];
                
                IOUtils.readFully(dis, data);
                RecordedBlock recordedBlock = (RecordedBlock) serializer.deserialize(data);
                
                if (lastTime != null) {
                    Duration duration = Duration.between(lastTime, recordedBlock.getTime());
                    Thread.sleep(duration.toMillis());
                }
                
                List<Message> messages
                        = recordedBlock.getMessages().stream()
                        .map(x -> {
                            return new Message(
                                    x.getSrcAddress(),
                                    AddressUtils.parentize(dstAddress, x.getDstSuffix()),
                                    x.getMessage());
                        })
                        .collect(Collectors.toList());
                dstShuttle.send(messages);
                
                lastTime = recordedBlock.getTime();
            }
            
        } catch (Exception e) {
            if (e instanceof EOFException) {
                LOG.info("Stopping read thread (end of file)");
            } else if (e instanceof InterruptedException) {
                LOG.info("Stopping read thread (interrupted)");
            } else {
                LOG.error("Error in read thread", e);
            }
        }
    }

}
