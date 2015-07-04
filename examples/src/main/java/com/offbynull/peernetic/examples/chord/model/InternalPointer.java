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

import java.util.Objects;
import org.apache.commons.lang3.Validate;

/**
 * A {@link Pointer} that represents self node.
 * @author Kasra Faghihi
 */
public final class InternalPointer implements Pointer {
    private NodeId id;

    /**
     * Constructs a {@link InternalPointer} object.
     * @param id id
     * @throws NullPointerException if any arguments are {@code null}
     */
    public InternalPointer(NodeId id) {
        Validate.notNull(id);
        
        this.id = id;
    }

    /**
     * Get ID.
     * @return id
     */
    @Override
    public NodeId getId() {
        return id;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final InternalPointer other = (InternalPointer) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "InternalPointer{" + "id=" + id + '}';
    }
}
