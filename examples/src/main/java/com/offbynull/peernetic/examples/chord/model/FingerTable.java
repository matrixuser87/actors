/*
 * Copyright (c) 2013-2014, Kasra Faghihi, All rights reserved.
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
package com.offbynull.peernetic.examples.chord.model;

import com.offbynull.peernetic.core.shuttle.Address;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import org.apache.commons.lang3.Validate;

/**
 * Holds on to routing information. For more information on how a finger table
 * operates, please refer to the Chord research paper. This implementation makes
 * some minor additions to the original finger table algorithm to ensure that
 * that there aren't any inconsistencies. That is, this implementation
 * guarantees that...
 * <ol>
 * <li>
 * Fingers that point to the base pointer show up for a contiguous range from
 * the entry after the last non-base entry (or 0 if there are no non-base
 * entries) all the way to the last entry in the finger table. For example:
 * <p/>
 * Imagine that you have just created a finger table and the base id is 0:<br/>
 * [0, 0, 0, 0, 0, 0].
 * <p/>
 * You insert a finger with id 8:<br/>
 * [8, 8, 8, 8, 0, 0].
 * <p/>
 * </li>
 * <li>An inserted finger will propagate backwards until it finds an entry that
 * isn't the base entry and isn't the same id as the id being replaced, but is
 * greater than or equal to the expected id. For example:
 * <p/>
 * Imagine that you have just created a finger table and the base id is 0:<br/>
 * [0, 0, 0, 0, 0, 0].
 * <p/>
 * You insert a finger with id 16:<br/>
 * [16, 16, 16, 16, 16, 0].
 * <p/>
 * You insert a finger with id 2:<br/>
 * [2, 2, 16, 16, 16, 0].
 * <p/>
 * You insert a finger with id 8:<br/>
 * [2, 2, 8, 8, 16, 0].
 * <p/>
 * You insert a finger with id 4:<br/>
 * [2, 2, 4, 8, 16, 0].
 * </li>
 * <li>A finger being removed will propagate backwards until it finds an entry
 * that isn't the base (it will never be base -- based on previous guarantees)
 * and isn't the same id as the id being removed. The replacement value for the
 * finger being removed will be the finger in front of it (or base if it's the
 * last finger and there are no other fingers in front of it). For example:
 * <p/>
 * Imagine that you have just created a finger table and the base id is 0:<br/>
 * [0, 0, 0, 0, 0, 0].
 * <p/>
 * You insert a finger with id 16:<br/>
 * [16, 16, 16, 16, 16, 0].
 * <p/>
 * You insert a finger with id 2:<br/>
 * [2, 2, 16, 16, 16, 0].
 * <p/>
 * You insert a finger with id 8:<br/>
 * [2, 2, 8, 8, 16, 0].
 * <p/>
 * You remove a finger with id 8:<br/>
 * [2, 2, 16, 16, 16, 0].
 * <p/>
 * You remove a finger with id 16:<br/>
 * [2, 2, 0, 0, 0, 0].
 * <p/>
 * You remove a finger with id 2:<br/>
 * [0, 0, 0, 0, 0, 0].
 * </li>
 * </ol>
 *
 * @author Kasra Faghihi
 */
public final class FingerTable {

    /**
     * Internal table that keeps track of fingers.
     */
    private List<InternalEntry> table;
    /**
     * Base (self) pointer.
     */
    private InternalPointer basePtr;
    /**
     * The bit count in {@link #basePtr}.
     */
    private int bitCount;

    /**
     * Constructs a {@link FingerTable}. All fingers are initialized to
     * {@code base}.
     *
     * @param basePtr base pointer
     * @throws NullPointerException if any arguments are {@code null}
     */
    public FingerTable(InternalPointer basePtr) {
        Validate.notNull(basePtr);
        NodeId baseId = basePtr.getId();
//        Validate.isTrue(NodeIdUtils.isUseableId(baseId)); // make sure satisfies 2^n-1

        this.basePtr = basePtr;

        this.bitCount = baseId.getBitLength();
        byte[] limit = baseId.getLimitAsByteArray();

        table = new ArrayList<>(bitCount);
        for (int i = 0; i < bitCount; i++) {
            BigInteger data = BigInteger.ONE.shiftLeft(i);
            byte[] offsetIdRaw = data.toByteArray();
            NodeId offsetId = new NodeId(offsetIdRaw, bitCount);
            NodeId expectedId = NodeId.add(baseId, offsetId);

            InternalEntry te = new InternalEntry();
            te.expectedId = expectedId;
            te.pointer = basePtr;

            table.add(te);
        }
    }

    /**
     * Searches the finger table for the closest to the id being searched for (closest in terms of being {@code <}).
     *
     * @param id id being searched for
     * @param ignoreIds list of ids to ignore when searching this finger table
     * @return closest preceding pointer, or maximum non-base if {@code id} is base id
     * @throws NullPointerException if any arguments are {@code null}
     * @throws IllegalArgumentException if {@code id}'s has a different limit bit size than base pointer's id
     */
    public Pointer findClosestPreceding(NodeId id, NodeId ... ignoreIds) {
        Validate.notNull(id);
        Validate.noNullElements(ignoreIds);
        Validate.isTrue(id.getBitLength() == bitCount);

        List<NodeId> skipIdList = Arrays.asList(ignoreIds);
        NodeId selfId = basePtr.getId();
        
        if (selfId.equals(id)) {
            return getMaximumNonBase(); // returns null if fingertable is empty (all fingers set to base)
        }

        InternalEntry foundEntry = null;
        ListIterator<InternalEntry> lit = table.listIterator(table.size());
        while (lit.hasPrevious()) {
            InternalEntry ie = lit.previous();
            NodeId fingerId = ie.pointer.getId();
            // if finger should be skipped, ignore it
            if (skipIdList.contains(fingerId)) {
                continue;
            }
            // if finger[i] exists between n (exclusive) and id (exclusive)
            // then return it
            if (fingerId.isWithin(selfId, false, id, false)) {
                foundEntry = ie;
                break;
            }
        }

        return foundEntry == null ? basePtr : foundEntry.pointer;
    }

    /**
     * Puts a pointer in to the finger table. See the constraints / guarantees mentioned in the class Javadoc: {@link FingerTable}.
     * <p/>
     * This method automatically determines the correct position for the finger.  The old pointer in that finger will be replaced by
     * {@code ptr}.
     *
     * @param ptr pointer to put in as finger
     * @throws NullPointerException if any arguments are {@code null}
     * @throws IllegalArgumentException if {@code ptr}'s id has a different limit bit size than the base pointer's id, or if {@code ptr} has 
     * an id that matches the base pointer's id
     */
    public void put(ExternalPointer ptr) {
        Validate.notNull(ptr);
        
        NodeId id = ptr.getId();
        NodeId baseId = basePtr.getId();

        Validate.isTrue(id.getBitLength() == bitCount);
        Validate.isTrue(!id.equals(baseId));

        // search for position to insert to
        int replacePos = -1;
        for (int i = 0; i < table.size(); i++) {
            InternalEntry ie = table.get(i);
            NodeId goalId = ie.expectedId;
            int compVal = NodeId.comparePosition(baseId, goalId, id);
            if (compVal < 0) {
                replacePos = i;
            } else if (compVal == 0) {
                replacePos = i;
                break;
            }
        }

        if (replacePos == -1) {
            return;
        }

        // replace in table
        InternalEntry entry = table.get(replacePos);
        entry.pointer = ptr;


        // replace immediate preceding neighbours if they exceed replacement
        for (int i = replacePos - 1; i >= 0; i--) {
            InternalEntry priorEntry = table.get(i);
            NodeId priorEntryId = priorEntry.pointer.getId();
            
            if (NodeId.comparePosition(baseId, priorEntryId, id) > 0 || priorEntryId.equals(baseId)) {
                priorEntry.pointer = ptr;
            } else {
                break;
            }
        }
    }

    /**
     * Replaces a pointer in to the finger table. Similar to
     * {@link #put(com.offbynull.peernetic.demos.chord.messages.core.ExternalPointer)}, but makes sure that {@code ptr} is less than or
     * equal to the expected id before putting it in.
     * <p/>
     * For example, imagine a finger table for a base pointer with an id of 0 and a bit count of 3. This is what the initial table would
     * look like...
     * <pre>
     * Index 0 = id:0 (base)
     * Index 1 = id:0 (base)
     * Index 2 = id:0 (base)
     * </pre>
     * If a value pointer with id of 6 were put in here, then a pointer with id of 1 were put in here, the table would look like this...
     * <pre>
     * Index 0 = id:1 (base)
     * Index 1 = id:6 (base)
     * Index 2 = id:6 (base)
     * </pre>
     * If this method were called with a pointer that had id of 7, nothing would happen. If this method were called with a pointer that had
     * id of 5, then the table would be adjusted to look like this...
     * <pre>
     * Index 0 = id:1 (base)
     * Index 1 = id:5 (base)
     * Index 2 = id:5 (base)
     * </pre>
     *
     * @return {@code true} if one or more entries were replaced, {@code false} otherwise
     * @param ptr pointer to add in as finger
     * @throws NullPointerException if any arguments are {@code null}
     * @throws IllegalArgumentException if {@code ptr}'s id has a different limit bit size than the base pointer's id, or if {@code ptr} has
     * an id that matches the base pointer's id
     */
    public boolean replace(ExternalPointer ptr) {
        Validate.notNull(ptr);
        Validate.isTrue(ptr.getId().getBitLength() == bitCount);

        NodeId id = ptr.getId();
        NodeId baseId = basePtr.getId();

        Validate.isTrue(id.getBitLength() == bitCount);
        Validate.isTrue(!id.equals(baseId));

        // search for position to insert to
        int replacePos = -1;
        for (int i = 0; i < table.size(); i++) {
            InternalEntry ie = table.get(i);
            NodeId goalId = ie.expectedId;
            int compVal = NodeId.comparePosition(baseId, goalId, id);
            if (compVal < 0) {
                replacePos = i;
            } else if (compVal == 0) {
                replacePos = i;
                break;
            }
        }

        if (replacePos == -1) {
            return false;
        }


        // check if can be replaced -- if so, replace.
        InternalEntry entry = table.get(replacePos);
        NodeId entryId = entry.pointer.getId();

        NodeId oldId;
        if (NodeId.comparePosition(baseId, id, entryId) < 0 || entryId.equals(baseId)) {
            oldId = entry.pointer.getId();
            entry.pointer = ptr;

            // replace immediate preceding neighbours if they = old value
            for (int i = replacePos - 1; i >= 0; i--) {
                InternalEntry priorEntry = table.get(i);
                NodeId priorEntryId = priorEntry.pointer.getId();

                if (priorEntryId.equals(oldId)) {
                    priorEntry.pointer = ptr;
                } else {
                    break;
                }
            }
        }
        
        return true;
    }

    /**
     * Get the last/maximum finger that isn't set to base. If no such finger is found, gives back {@code null}. See the
     * constraints/guarantees mentioned in the Javadoc header {@link FingerTable}.
     * @return last/max non-base finger, or {@code null} if no such finger exists
     */
    public ExternalPointer getMaximumNonBase() {
        for (int i = bitCount - 1; i >= 0; i--) {
            InternalEntry ie = table.get(i);
            if (!(ie.pointer instanceof InternalPointer)) { // equiv to ie.actualPointer.getId().equals(basePtr.getId()), since we're
                                                                  // only ever allowed to have references to ourself / our own id via a
                                                                  // InternalPointer
                return (ExternalPointer) ie.pointer;
            }
        }

        return null;
    }

    /**
     * Get the finger at a specific index.
     *
     * @param idx finger index
     * @return finger at index {@code idx}
     * @throws IllegalArgumentException if {@code idx < 0 || idx > table.size()} (size of table = bit size of base pointer's limit)
     */
    public Pointer get(int idx) {
        Validate.inclusiveBetween(0, table.size() - 1, idx);

        InternalEntry ie = table.get(idx);
        return ie.pointer;
    }

    /**
     * Get the id expected for a finger position. For example, if base id is 0, finger pos 0 expects 1, finger pos 1 expects 2, finger pos 2
     * expects 4, finger pos 3 expects 8, etc... For more information, see Chord research paper.
     * @param idx finger position to get expected id for
     * @return expected id for a specific finger position
     * @throws IllegalArgumentException if {@code idx < 0 || idx > table.size()} (size of table = bit size of base pointer's limit)
     */
    public NodeId getExpectedId(int idx) {
        Validate.inclusiveBetween(0, table.size() - 1, idx);

        InternalEntry ie = table.get(idx);
        return ie.expectedId;
    }

    /**
     * Get the id of other nodes that should have this node in its finger table. For example, for a 16 node ring with base id 8, router
     * position 0 expects 7, router position 1 expects 6, router position 2 expects 4, and router position 3 expects 0. For more
     * information, see Chord research paper.
     * @param idx router position to get expected id for
     * @return expected id for a specific router position
     * @throws IllegalArgumentException if {@code idx < 0 || idx > table.size()} (size of table = bit size of base pointer's limit)
     */
    public NodeId getRouterId(int idx) {
        Validate.inclusiveBetween(0, table.size() - 1, idx);

        BigInteger data = BigInteger.ONE.shiftLeft(idx);
        byte[] offsetIdRaw = data.toByteArray();
        NodeId offsetId = new NodeId(offsetIdRaw, basePtr.getId().getBitLength());
        NodeId routerId = NodeId.subtract(basePtr.getId(), offsetId);
        
        return routerId;
    }

    /**
     * Removes a pointer in to the finger table. If the pointer doesn't exist in the finger table, does nothing. See the
     * constraints/guarantees mentioned in the class Javadoc: {@link FingerTable}.
     * @param ptr pointer to put in as finger
     * @throws NullPointerException if any arguments are {@code null}
     * @throws IllegalArgumentException if {@code ptr}'s id has a different limit bit size than the base pointer's id, or if {@code ptr} has
     * an id that matches the base pointer's id
     */
    public void remove(ExternalPointer ptr) {
        Validate.notNull(ptr);
        Validate.isTrue(ptr.getId().getBitLength() == bitCount);

        NodeId id = ptr.getId();
        String address = ptr.getLinkId();
        NodeId baseId = basePtr.getId();

        Validate.isTrue(id.getBitLength() == bitCount);
        Validate.isTrue(!id.equals(baseId));

        ListIterator<InternalEntry> lit = table.listIterator(table.size());
        while (lit.hasPrevious()) {
            InternalEntry ie = lit.previous();
            
            if (ie.pointer instanceof InternalPointer) { // don't bother inspecting internal pointers since we can only remove
                                                               // external pointers
                continue;
            }
            
            ExternalPointer testPtr = (ExternalPointer) ie.pointer;
            NodeId testId = testPtr.getId();
            String testAddress = testPtr.getLinkId();

            if (id.equals(testId) && address.equals(testAddress)) {
                remove(lit.previousIndex() + 1);
                break;
            }
        }
    }
    
    /**
     * Removes the pointer at index {@code idx} of the finger table. See the constraints / guarantees mentioned in the class Javadoc:
     * {@link FingerTable}.
     *
     * @param idx finger position to clear
     * @throws IllegalArgumentException if {@code ptr}'s id has a different limit bit size than the base pointer's id, or if {@code ptr} has
     * an id that matches the base pointer's id.
     */
    private void remove(int idx) {
        Validate.inclusiveBetween(0, bitCount - 1, idx);

        NodeId baseId = basePtr.getId();

        // save existing id
        InternalEntry entry = table.get(idx);
        NodeId oldId = entry.pointer.getId();

        if (oldId.equals(baseId)) {
            // nothing to remove if self... all forward entries sohuld
            // also be self in this case
            return;
        }

        // get next id in table, if available
        Pointer nextPtr;

        if (idx < bitCount - 1) {
            // set values if there's a next available (if we aren't at ceiling)
            InternalEntry nextEntry = table.get(idx + 1);
            nextPtr = nextEntry.pointer;
        } else {
            // set values to self if we are at the ceiling (full circle)
            nextPtr = basePtr;
        }

        // replace prior ids with next id if prior same as old id and is
        // contiguous... prior ids will never be greater than the old id
        for (int i = idx; i >= 0; i--) {
            InternalEntry priorEntry = table.get(i);
            NodeId priorEntryId = priorEntry.pointer.getId();

            if (priorEntryId.equals(oldId)) {
                priorEntry.pointer = nextPtr;
            } else {
                break;
            }
        }
    }

    /**
     * Removes all fingers before {@code id} (does not remove {@code id} itself).
     *
     * @param id id of which all fingers before it will be removed
     * @return number of fingers that were cleared
     * @throws NullPointerException if any arguments are {@code null}
     * @throws IllegalArgumentException if {@code id} has a different limit bit size than base pointer's id, or if {@code id} is equivalent
     * to base pointer's id
     */
    public int clearBefore(NodeId id) {
        Validate.notNull(id);
        Validate.isTrue(id.getBitLength() == bitCount);

        NodeId baseId = basePtr.getId();

        if (id.equals(baseId)) {
            throw new IllegalArgumentException();
        }

        ListIterator<InternalEntry> lit = table.listIterator(table.size());
        while (lit.hasPrevious()) {
            InternalEntry ie = lit.previous();
            NodeId testId = ie.pointer.getId();

            if (NodeId.comparePosition(baseId, id, testId) > 0) {
                int position = lit.previousIndex() + 1;
                clearBefore(position);
                return position;
            }
        }

        return 0;
    }

    /**
     * Removes all fingers before position {@code idx} (does not remove finger at {@code idx}).
     * @param idx position which all fingers before it will be removed
     * @throws IllegalArgumentException if {@code idx < 0 || idx > table.size()}
     */
    private void clearBefore(int idx) {
        Validate.inclusiveBetween(0, bitCount - 1, idx);

        // get next id in table, if available
        Pointer nextPtr;

        if (idx < bitCount - 1) {
            // set values if there's a next available (if we aren't at ceiling)
            InternalEntry nextEntry = table.get(idx + 1);
            nextPtr = nextEntry.pointer;
        } else {
            // set values to self if we are at the ceiling (full circle)
            nextPtr = basePtr;
        }

        // replace prior ids with next id... prior ids will never be greater
        // than the id at the position we're removing
        for (int i = idx; i >= 0; i--) {
            InternalEntry priorEntry = table.get(i);
            priorEntry.pointer = nextPtr;
        }
    }

    /**
     * Clears the finger table. All fingers will be set to the base pointer.
     */
    public void clear() {
        // replace entries with self id all the way till the end...
        for (int i = 0; i < bitCount; i++) {
            InternalEntry priorEntry = table.get(i);
            priorEntry.pointer = basePtr;
        }
    }

    /**
     * Searches the finger table for the left-most occurrence of {@code ptr}.
     * @param ptr pointer to search for
     * @return index of occurrence, or -1 if not found
     * @throws NullPointerException if any arguments are {@code null}
     * @throws IllegalArgumentException if {@code ptr}'s id has a different limit bit size than the base pointer's id
     */
    public int getMinimumIndex(Pointer ptr) {
        Validate.notNull(ptr);
        Validate.isTrue(ptr.getId().getBitLength() == bitCount);

        NodeId id = ptr.getId();
        Validate.isTrue(id.getBitLength() == bitCount);

        ListIterator<InternalEntry> lit = table.listIterator();
        while (lit.hasNext()) {
            InternalEntry ie = lit.next();
            
            if (ie.pointer.equals(ptr)) {
                return lit.nextIndex() - 1;
            }
        }
        
        return -1;
    }

    /**
     * Dumps the fingers.
     *
     * @return list of fingers
     */
    public List<Pointer> dump() {
        List<Pointer> ret = new ArrayList<>(table.size());
        table.stream().forEach(ie -> ret.add(ie.pointer));

        return ret;
    }

    /**
     * Internal class to keep track of a finger.
     */
    private final class InternalEntry {

        /**
         * Desired id for finger.
         */
        private NodeId expectedId;
        /**
         * Pointer this finger is pointing to (if self set to {@link InternalPointer}, otherwise set to {@link ExternalPointer}).
         */
        private Pointer pointer;
    }
}
