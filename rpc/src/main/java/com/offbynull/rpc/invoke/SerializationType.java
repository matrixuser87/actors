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
package com.offbynull.rpc.invoke;

/**
 * Type of serialized invokation data.
 * @author Kasra F
 */
public enum SerializationType {

    /**
     * Method invokation data.
     */
    METHOD_CALL,
    /**
     * Method result data.
     */
    METHOD_RETURN,
    /**
     * Method exception data.
     */
    METHOD_THROW,
}
