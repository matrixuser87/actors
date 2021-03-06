/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
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
package com.offbynull.actors.core.gateway.servlet;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.offbynull.actors.core.shuttle.Message;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Validate;

final class HttpToSystemBundleJsonDeserializer implements JsonDeserializer<HttpToSystemBundle> {

    private final String prefix;
    private final Type messageListType;
    
    public HttpToSystemBundleJsonDeserializer(String prefix) {
        Validate.notNull(prefix);
        this.prefix = prefix;
        
        messageListType = new TypeToken<ArrayList<Message>>() { }.getType();
    }

    @Override
    public HttpToSystemBundle deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Validate.notNull(json);
        Validate.notNull(typeOfT);
        Validate.notNull(context);

        JsonObject jsonObject = json.getAsJsonObject();

        String httpAddressId = jsonObject.get("httpAddressId").getAsString();
        int httpToSystemOffset = jsonObject.get("httpToSystemOffset").getAsInt();
        int systemToHttpOffset = jsonObject.get("systemToHttpOffset").getAsInt();
        List<Message> messages = context.deserialize(jsonObject.get("messages").getAsJsonArray(), messageListType);
        
        messages.forEach(msg -> {
            Validate.isTrue(msg.getSourceAddress().size() >= 2);
            String srcPrefix = msg.getSourceAddress().getElement(0);
            String srcId = msg.getSourceAddress().getElement(1);
            Validate.isTrue(srcPrefix.equals(prefix));
            Validate.isTrue(srcId.equals(httpAddressId));
        });
        
        return new HttpToSystemBundle(httpAddressId, httpToSystemOffset, systemToHttpOffset, messages);
    }
}
