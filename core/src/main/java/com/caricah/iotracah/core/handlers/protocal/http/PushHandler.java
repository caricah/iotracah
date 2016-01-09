/*
 *
 * Copyright (c) 2016 Caricah <info@caricah.com>.
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

package com.caricah.iotracah.core.handlers.protocal.http;

import com.caricah.iotracah.core.handlers.PublishAcknowledgeHandler;
import com.caricah.iotracah.core.worker.state.messages.AcknowledgeMessage;
import com.caricah.iotracah.core.worker.state.messages.PublishMessage;
import com.caricah.iotracah.exceptions.RetriableException;
import com.caricah.iotracah.exceptions.UnRetriableException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.body.MultipartBody;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * @author <a href="mailto:bwire@caricah.com"> Peter Bwire </a>
 * @version 1.0 1/9/16
 */
public class PushHandler {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Logger log = LoggerFactory.getLogger(PushHandler.class);

    public void pushToUrl(String url, PublishMessage publishMessage) {


        ByteBuffer payloadBuffer = ByteBuffer.wrap((byte[]) publishMessage.getPayload());

        String payload = UTF8.decode(payloadBuffer).toString();


        MultipartBody httpMessage = Unirest.post(url)
                .header("accept", "application/json")
                .field("topic", publishMessage.getTopic())
                .field("message", payload);

        if (MqttQoS.AT_LEAST_ONCE.value() == publishMessage.getQos()) {

            httpMessage.asJsonAsync(new Callback<JsonNode>() {

                public void failed(UnirestException e) {
                    log.info(" httpPushToUrl failed : problems calling service", e);
                }

                public void completed(HttpResponse<JsonNode> response) {
                    int code = response.getStatus();

                    JsonNode responseBody = response.getBody();
                    log.info(" httpPushToUrl completed : external server responded with {}", responseBody);
                    if (200 == code) {

                        AcknowledgeMessage ackMessage = AcknowledgeMessage.from(publishMessage.getMessageId());
                        ackMessage.copyBase(publishMessage);

                        PublishAcknowledgeHandler publishAcknowledgeHandler = new PublishAcknowledgeHandler(ackMessage);
                        try {
                            publishAcknowledgeHandler.handle();
                        } catch (RetriableException | UnRetriableException e) {
                            log.warn(" httpPushToUrl completed : problem closing connection. ");
                        }
                    }
                }

                public void cancelled() {
                    log.info(" httpPushToUrl cancelled : request cancelled.");
                }

            });
        } else {
            httpMessage.asJsonAsync();
        }

    }
}