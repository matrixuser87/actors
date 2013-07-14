package com.offbynull.p2prpc.io;

import java.io.IOException;

public interface Server {
    void start(ServerCallback callback) throws IOException;
    void stop() throws IOException;
}
