/*
 *
 * Copyright (c) 2015 Caricah <info@caricah.com>.
 *
 * Caricah licenses this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 *  OF ANY  KIND, either express or implied.  See the License for the specific language
 *  governing permissions and limitations under the License.
 *
 *
 *
 *
 */

package com.caricah.iotracah.bootstrap.data.messages;

import com.caricah.iotracah.bootstrap.data.messages.base.IOTMessage;

/**
 * @author <a href="mailto:bwire@caricah.com"> Peter Bwire </a>
 */
public final class AcknowledgeMessage extends IOTMessage {

    public static final String MESSAGE_TYPE = "PUBACK";

    private final int messageId;

    public static AcknowledgeMessage from(int messageId) {
        if (messageId < 1 ) {
            throw new IllegalArgumentException("messageId: " + messageId + " (expected: > 1)");
        }
        return new AcknowledgeMessage(messageId);
    }

    private AcknowledgeMessage(int messageId) {

        this. messageId = messageId;
        setMessageType(MESSAGE_TYPE);

    }

    public boolean isDup() {
        return false;
    }

    public int getQos() {
        return 0;
    }

    public boolean isRetain() {
        return false;
    }

    public int getMessageId() {
        return messageId;
    }

    @Override
    public String toString() {
        return getClass().getName() + '[' + "messageId=" + getMessageId() + ']';
    }
}
