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

package com.caricah.iotracah.core.handlers;


import com.caricah.iotracah.core.security.AuthorityRole;
import com.caricah.iotracah.core.worker.state.messages.PublishMessage;
import com.caricah.iotracah.core.worker.state.messages.RetainedMessage;
import com.caricah.iotracah.core.worker.state.messages.SubscribeAcknowledgeMessage;
import com.caricah.iotracah.core.worker.state.messages.SubscribeMessage;
import com.caricah.iotracah.core.worker.state.models.Client;
import com.caricah.iotracah.core.worker.state.models.SubscriptionFilter;
import com.caricah.iotracah.exceptions.RetriableException;
import com.caricah.iotracah.exceptions.UnRetriableException;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:bwire@caricah.com"> Peter Bwire </a>
 */
public class SubscribeHandler extends RequestHandler<SubscribeMessage> {

    /**
     * The SUBSCRIBE Packet is sent from the Client to the Server to create one or more
     * Subscriptions. Each SubscriptionFilter registers a Client’s interest in one or more Topics.
     * The Server sends PUBLISH Packets to the Client in order to forward Application Messages
     * that were published to Topics that match these Subscriptions.
     * The SUBSCRIBE Packet also specifies (for each SubscriptionFilter) the maximum QoS with which
     * the Server can send Application Messages to the Client
     *
     * @throws RetriableException
     * @throws UnRetriableException
     */
    @Override
    public void handle(SubscribeMessage subscribeMessage) throws RetriableException, UnRetriableException {

        log.debug(" handle : begining to handle a subscription {}.", subscribeMessage);

           /**
             * First we obtain the client responsible for this connection.
             */

            List<Integer> grantedQos = new ArrayList<>();


                /**
                 * Before subscribing we should get the current session and validate it.
                 */

                    List<String> topics = new ArrayList<>();
                    subscribeMessage.getTopicFilterList().forEach(topic -> topics.add(topic.getKey()));

                        Observable<Client> permissionObservable = checkPermission(subscribeMessage.getSessionId(),
                                subscribeMessage.getAuthKey(), AuthorityRole.SUBSCRIBE, topics);

                        permissionObservable.subscribe(
                                (client)->{

                                    //We have all the security to proceed.
                            Observable<Map.Entry<String, Integer>> subscribeObservable = getMessenger().subscribe(client, subscribeMessage.getTopicFilterList());

                            subscribeObservable.subscribe(
                                    (entry) -> grantedQos.add(entry.getValue()),
                                    throwable1 ->  disconnectDueToError(throwable1, subscribeMessage),
                                    () -> {

                                        /**
                                         * Save subscription payload
                                         */
                                        if(subscribeMessage.getProtocal().isNotPersistent()){
                                            client.setProtocalData(subscribeMessage.getReceptionUrl());
                                            getDatastore().saveClient(client);
                                        }

                                        SubscribeAcknowledgeMessage subAckMessage = SubscribeAcknowledgeMessage.from(
                                                subscribeMessage.getMessageId(), grantedQos);
                                        subAckMessage.copyBase(subscribeMessage);
                                        pushToServer(subAckMessage);


                                        /**
                                         * Queue retained messages to our subscriber.
                                         */


                                        int count = 0;
                                        for(Map.Entry<String, Integer> entry : subscribeMessage.getTopicFilterList())
                                            if (grantedQos.get(count++) != 0x80) {
                                                Observable<SubscriptionFilter> subscriptionFilterObservable = getDatastore().getSubscriptionFilter(client.getPartition(), entry.getKey());
                                                subscriptionFilterObservable.subscribe(
                                                        subscriptionFilter -> {

                                                            try {

                                                                Observable<RetainedMessage> retainedMessageObservable = getDatastore().getRetainedMessage(client.getPartition(), (String) subscriptionFilter.generateIdKey());
                                                                retainedMessageObservable.subscribe(retainedMessage -> {


                                                                    PublishMessage publishMessage = retainedMessage.toPublishMessage();
                                                                    publishMessage.setPartition(client.getPartition());
                                                                    publishMessage.setClientId(client.getClientId());
                                                                    publishMessage.copyBase(subscribeMessage);

                                                                    if (publishMessage.getQos() > 0) {
                                                                        publishMessage.setReleased(false);
                                                                        //Save the message as we proceed.
                                                                        getDatastore().saveMessage(publishMessage);
                                                                    }


                                                                    try {

                                                                        publishMessage.setProtocalData( client.getProtocalData());
                                                                        getWorker().getHandler(PublishOutHandler.class).handle(publishMessage);

                                                                        log.info(" handle : we got to release a retained message.");

                                                                    } catch (RetriableException | UnRetriableException e) {
                                                                        log.error(" handle : problems publishing ", e);
                                                                    }


                                                                }, throwable -> {});

                                                            } catch (UnRetriableException e) {
                                                                log.error("  handle : problems obtaining subscription filter key", e);
                                                            }
                                                        }
                                                );

                                            }

                                    });
                        }, throwable2 -> disconnectDueToError(throwable2, subscribeMessage));


    }
}
