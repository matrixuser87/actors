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
package com.offbynull.rpc.transport.udp;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Random;

final class MessageIdGenerator {
    private Random random;
    
    public MessageIdGenerator() {
        SecureRandom secureRandom = new SecureRandom();
        
        random = new Random(secureRandom.nextLong());
    }

    public MessageId generate() {
        long time = System.currentTimeMillis();
        long rand = random.nextLong();
        
        // This is not secure. Already worked out a method where you may be able to work backwards to the seed if you have enough rand
        // values.
        
        return new MessageId(ByteBuffer.allocate(16).putLong(rand).putLong(time).array());
    }
}
