package com.offbynull.peernetic.common.message;

import java.time.Duration;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

public class NonceManagerTest {
    
    private static final String NONCE_1_RESPONSE = "resp1";
    private static final String NONCE_2_RESPONSE = "resp2";
    
    @Test
    public void basicNonceManagerTest() {
        NonceGenerator<byte[]> nonceGenerator = new ByteArrayNonceGenerator(16);
        NonceManager<byte[]> nonceManager = new NonceManager<>();
        
        Nonce<byte[]> nonce1 = nonceGenerator.generate();
        Nonce<byte[]> nonce2 = nonceGenerator.generate();
        
        Instant startTime = Instant.ofEpochMilli(0L);
        Instant nextTime = startTime;
        
        Assert.assertFalse(nonceManager.isNoncePresent(nonce1));
        Assert.assertFalse(nonceManager.isNoncePresent(nonce2));
        
        nonceManager.addNonce(nextTime, Duration.ofSeconds(5L), nonce1, NONCE_1_RESPONSE);
        nonceManager.addNonce(nextTime, Duration.ofSeconds(10L), nonce2, null);
        Assert.assertEquals(NONCE_1_RESPONSE, nonceManager.getNonceValue(nonce1));
        Assert.assertEquals(null, nonceManager.getNonceValue(nonce2));

        nonceManager.assignValue(nonce2, NONCE_2_RESPONSE);
        Assert.assertEquals(NONCE_1_RESPONSE, nonceManager.getNonceValue(nonce1));
        Assert.assertEquals(NONCE_2_RESPONSE, nonceManager.getNonceValue(nonce2));
        
        nextTime = startTime.plusSeconds(1L);
        nonceManager.process(nextTime);
        Assert.assertEquals(NONCE_1_RESPONSE, nonceManager.getNonceValue(nonce1));
        Assert.assertEquals(NONCE_2_RESPONSE, nonceManager.getNonceValue(nonce2));
        
        nextTime = startTime.plusSeconds(2L);
        nonceManager.process(nextTime);
        Assert.assertEquals(NONCE_1_RESPONSE, nonceManager.getNonceValue(nonce1));
        Assert.assertEquals(NONCE_2_RESPONSE, nonceManager.getNonceValue(nonce2));
        
        nextTime = startTime.plusSeconds(5L);
        nonceManager.process(nextTime);
        Assert.assertFalse(nonceManager.isNoncePresent(nonce1));
        Assert.assertEquals(NONCE_2_RESPONSE, nonceManager.getNonceValue(nonce2));
    }
}
