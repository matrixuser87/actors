package com.offbynull.p2prpc;

import com.offbynull.p2prpc.invoke.Capturer;
import com.offbynull.p2prpc.invoke.CapturerCallback;
import com.offbynull.p2prpc.invoke.InvokeThreadInformation;
import com.offbynull.p2prpc.invoke.Invoker;
import com.offbynull.p2prpc.invoke.InvokerCallback;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Exchanger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public final class InvokeTest {

    public InvokeTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void simpleInvokeTest() {
        AtomicBoolean failFlag = new AtomicBoolean();
        
        FakeObject server = Mockito.mock(FakeObject.class);
        FakeObject client = generateStub(server, failFlag, Collections.emptyMap());

        client.fakeMethodCall();
        
        Assert.assertFalse(failFlag.get());
    }

    @Test
    public void simpleInvokeWithInfoTest() {
        AtomicBoolean failFlag = new AtomicBoolean();
        
        final Map<String, Object> info = new HashMap<>();
        info.put("TestKey1", "TestValue1");
        info.put("TestKey2", 2);
        
        FakeObject server = Mockito.mock(FakeObject.class);
        FakeObject client = generateStub(server, failFlag, info);
        
        Mockito.when(server.fakeMethodCall(Matchers.anyString())).thenAnswer(new Answer<Integer>() {

            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                Map<String, Object> grabbedInfo = InvokeThreadInformation.getInfoMap();
                Assert.assertEquals(info, grabbedInfo);
                return 5;
            }
            
        });

        int ret = client.fakeMethodCall("");
        
        Assert.assertFalse(failFlag.get());
        Assert.assertEquals(5, ret);
    }

    @Test
    public void advancedInvokeTest() {
        AtomicBoolean failFlag = new AtomicBoolean();
        
        FakeObject server = Mockito.mock(FakeObject.class);
        Mockito.when(server.fakeMethodCall("req msg", 0)).thenReturn("resp msg");
        
        FakeObject client = generateStub(server, failFlag, Collections.emptyMap());

        String resp = client.fakeMethodCall("req msg", 0);

        Assert.assertFalse(failFlag.get());
        Assert.assertEquals("resp msg", resp);
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwableInvokeTest() {
        AtomicBoolean failFlag = new AtomicBoolean();
        
        FakeObject server = Mockito.mock(FakeObject.class);
        Mockito.when(server.fakeMethodCall("req msg", 0)).thenThrow(new IllegalArgumentException("bad!"));
        
        FakeObject client = generateStub(server, failFlag, Collections.emptyMap());

        try {
            client.fakeMethodCall("req msg", 0);
        } finally {
            Assert.assertFalse(failFlag.get());
        }
    }

    private FakeObject generateStub(FakeObject obj, final AtomicBoolean failFlag,
            final Map<? extends Object, ? extends Object> invokeInfo) {
        final Invoker invoker = new Invoker(obj, 1);
        Capturer<FakeObject> capturer = new Capturer<>(FakeObject.class);
        
        FakeObject client = capturer.createInstance(new CapturerCallback() {

            @Override
            public byte[] invokationTriggered(byte[] data) {
                final Exchanger<byte[]> exchanger = new Exchanger<>();

                invoker.invoke(data, new InvokerCallback() {

                    @Override
                    public void invokationFailed(Throwable t) {
                        failFlag.set(true);
                    }

                    @Override
                    public void invokationFinised(byte[] data) {
                        try {
                            exchanger.exchange(data);
                        } catch (InterruptedException ex) {
                            failFlag.set(true);
                            throw new RuntimeException(ex);
                        }
                    }
                }, invokeInfo);

                try {
                    byte[] ret = exchanger.exchange(null);
                    return ret;
                } catch (InterruptedException ex) {
                    failFlag.set(true);
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public void invokationFailed(Throwable err) {
                failFlag.set(true);
                throw new RuntimeException(err);
            }
        });
        
        return client;
    }

    private interface FakeObject {

        void fakeMethodCall();

        int fakeMethodCall(String arg);

        String fakeMethodCall(int arg);

        String fakeMethodCall(String arg1, int arg2);

        int fakeMethodCall2(String arg);
    }
}