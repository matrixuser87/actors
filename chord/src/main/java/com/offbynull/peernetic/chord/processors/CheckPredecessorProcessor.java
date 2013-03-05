package com.offbynull.peernetic.chord.processors;

import com.offbynull.peernetic.chord.ChordState;
import com.offbynull.peernetic.chord.Pointer;
import com.offbynull.peernetic.chord.processors.QueryProcessor.QueryFailedProcessorException;
import com.offbynull.peernetic.eventframework.handler.IncomingEvent;
import com.offbynull.peernetic.eventframework.handler.TrackedIdGenerator;
import com.offbynull.peernetic.eventframework.processor.FinishedProcessResult;
import com.offbynull.peernetic.eventframework.processor.ProcessResult;
import com.offbynull.peernetic.eventframework.processor.Processor;

public final class CheckPredecessorProcessor implements Processor {
    private TrackedIdGenerator tidGen;
    private ChordState chordState;
    private State state;
    private int index;
    private Pointer testPtr;
    private QueryProcessor queryProc;

    public CheckPredecessorProcessor(ChordState chordState, int index,
            TrackedIdGenerator tidGen) {
        if (tidGen == null || chordState == null) {
            throw new NullPointerException();
        }
        
        if (index < 0 || index >= chordState.getBitCount()) {
            throw new IllegalArgumentException();
        }
        
        this.tidGen = tidGen;
        this.chordState = chordState;
        this.state = State.TEST;
        this.index = index;
    }

    @Override
    public ProcessResult process(long timestamp, IncomingEvent event) {
        switch (state) {
            case TEST:
                return processTestState(timestamp, event);
            case TEST_WAIT:
                return processTestWaitState(timestamp, event);
            case FINISHED:
                return processFinishedState(timestamp, event);
            default:
                throw new IllegalStateException();
        }
    }

    private ProcessResult processTestState(long timestamp,
            IncomingEvent event) {
        testPtr = chordState.getFinger(index);
        queryProc = new QueryProcessor(tidGen, testPtr.getAddress());
        ProcessResult queryProcRes = queryProc.process(timestamp, event);
        
        state = State.TEST_WAIT;
        
        return queryProcRes;
    }

    private ProcessResult processTestWaitState(long timestamp,
            IncomingEvent event) {
        try {
            return queryProc.process(timestamp, event);
        } catch (QueryFailedProcessorException qfe) {
            chordState.setPredecessor(null);
            return new FinishedProcessResult();
        }
    }

    private ProcessResult processFinishedState(long timestamp,
            IncomingEvent event) {
        throw new IllegalStateException();
    }

    
    private enum State {
        TEST,
        TEST_WAIT,
        FINISHED
    }
}