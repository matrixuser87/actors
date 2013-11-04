package com.offbynull.p2prpc.session;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Random;

public final class PacketIdGenerator {
    private Random firstRandom;
    private Random secondRandom;
    
    public PacketIdGenerator() {
        SecureRandom secureRandom = new SecureRandom();
        
        firstRandom = new Random(secureRandom.nextLong());
        secondRandom = new Random(secureRandom.nextLong());
    }

    public PacketId generate() {
        long time = System.currentTimeMillis();
        long rand = firstRandom.nextLong() ^ secondRandom.nextLong();
        
        // TODO: firstRandom and secondRandom need to be recreated every n iterations with new SecureRandom values.
        //
        // This is not secure. Already worked out a method where you may be able to work backwards to the seed if you have enough rand
        // values.
        
        return new PacketId(ByteBuffer.allocate(16).putLong(rand).putLong(time).array());
    }
}