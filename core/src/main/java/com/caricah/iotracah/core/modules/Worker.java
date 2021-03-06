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

package com.caricah.iotracah.core.modules;

import com.caricah.iotracah.bootstrap.data.messages.DisconnectMessage;
import com.caricah.iotracah.bootstrap.data.messages.PublishMessage;
import com.caricah.iotracah.bootstrap.data.messages.base.IOTMessage;
import com.caricah.iotracah.bootstrap.data.models.messages.IotMessageKey;
import com.caricah.iotracah.bootstrap.data.models.subscriptions.IotSubscription;
import com.caricah.iotracah.bootstrap.exceptions.RetriableException;
import com.caricah.iotracah.bootstrap.security.realm.state.IOTClient;
import com.caricah.iotracah.bootstrap.system.BaseSystemHandler;
import com.caricah.iotracah.core.handlers.RequestHandler;
import com.caricah.iotracah.core.modules.base.IOTBaseHandler;
import com.caricah.iotracah.core.modules.base.server.ServerRouter;
import com.caricah.iotracah.core.worker.exceptions.DoesNotExistException;
import com.caricah.iotracah.core.worker.state.Messenger;
import com.caricah.iotracah.core.worker.state.SessionResetManager;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicSequence;
import org.apache.shiro.session.SessionListener;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * @author <a href="mailto:bwire@caricah.com"> Peter Bwire </a>
 * @version 1.0 8/10/15
 */
public abstract class Worker extends IOTBaseHandler implements SessionListener{

    public static final String CORE_CONFIG_WORKER_ANNONYMOUS_LOGIN_ENABLED = "core.config.worker.annonymous.login.is.enabled";
    public static final boolean CORE_CONFIG_WORKER_ANNONYMOUS_LOGIN_ENABLED_DEFAULT_VALUE = true;

    public static final String CORE_CONFIG_WORKER_ANNONYMOUS_LOGIN_USERNAME = "core.config.worker.annonymous.login.username";
    public static final String CORE_CONFIG_ENGINE_WORKER_ANNONYMOUS_LOGIN_USERNAME_DEFAULT_VALUE = "annonymous_username";

    public static final String CORE_CONFIG_WORKER_ANNONYMOUS_LOGIN_PASSWORD = "core.config.worker.annonymous.login.password";
    public static final String CORE_CONFIG_ENGINE_WORKER_ANNONYMOUS_LOGIN_PASSWORD_DEFAULT_VALUE = "annonymous_password";

    public static final String CORE_CONFIG_WORKER_CLIENT_KEEP_ALIVE_IN_SECONDS = "core.config.worker.client.keep.alive.in.seconds";
    public static final int CORE_CONFIG_WORKER_CLIENT_KEEP_ALIVE_IN_SECONDS_DEFAULT_VALUE = 65535;

    private Ignite ignite;

    public Ignite getIgnite() {
        return ignite;
    }

    public void setIgnite(Ignite ignite) {
        this.ignite = ignite;
    }

    private boolean annonymousLoginEnabled;

    private String annonymousLoginUsername;

    private String annonymousLoginPassword;

    private String defaultPartitionName;

    private int keepAliveInSeconds;

    private Datastore datastore;

    private Messenger messenger;

    private ExecutorService executorService;

    private IgniteAtomicSequence atomicSequence;

    private Scheduler scheduler;

    private ServerRouter serverRouter;

    private SessionResetManager sessionResetManager;

    private static final HashMap<Class, RequestHandler> handlers = new HashMap<>();

    public Datastore getDatastore() {
        return datastore;
    }

    public void setDatastore(Datastore datastore) {
        this.datastore = datastore;
    }

    public Messenger getMessenger() {
        return messenger;
    }

    public void setMessenger(Messenger messenger) {
        this.messenger = messenger;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;

        setScheduler();
    }

    public IgniteAtomicSequence getAtomicSequence() {
        return atomicSequence;
    }

    public void setAtomicSequence(IgniteAtomicSequence atomicSequence) {
        this.atomicSequence = atomicSequence;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler() {
        this.scheduler = Schedulers.from(getExecutorService());
    }

    public ServerRouter getServerRouter() {
        return serverRouter;
    }

    public void setServerRouter(ServerRouter serverRouter) {
        this.serverRouter = serverRouter;
    }

    public SessionResetManager getSessionResetManager() {
        return sessionResetManager;
    }

    public void setSessionResetManager(SessionResetManager sessionResetManager) {
        this.sessionResetManager = sessionResetManager;
    }
    public boolean isAnnonymousLoginEnabled() {
        return annonymousLoginEnabled;
    }

    public void setAnnonymousLoginEnabled(boolean annonymousLoginEnabled) {
        this.annonymousLoginEnabled = annonymousLoginEnabled;
    }

    public String getAnnonymousLoginUsername() {
        return annonymousLoginUsername;
    }

    public void setAnnonymousLoginUsername(String annonymousLoginUsername) {
        this.annonymousLoginUsername = annonymousLoginUsername;
    }

    public String getAnnonymousLoginPassword() {
        return annonymousLoginPassword;
    }

    public void setAnnonymousLoginPassword(String annonymousLoginPassword) {
        this.annonymousLoginPassword = annonymousLoginPassword;
    }

    public String getDefaultPartitionName() {
        return defaultPartitionName;
    }

    public void setDefaultPartitionName(String defaultPartitionName) {
        this.defaultPartitionName = defaultPartitionName;
    }
    public int getKeepAliveInSeconds() {
        return keepAliveInSeconds;
    }

    public void setKeepAliveInSeconds(int keepAliveInSeconds) {
        this.keepAliveInSeconds = keepAliveInSeconds;
    }


    public <T extends RequestHandler> T getHandler(Class<T> t){
        return (T) handlers.get(t);
    }

    protected void addHandler(RequestHandler handler){
        handler.setWorker(this);
        handlers.put(handler.getClass(), handler);
    }

    /**
     * Sole receiver of all messages from the servers.
     *
     * @param IOTMessage
     */
    @Override
    public void onNext(IOTMessage IOTMessage) {

    }


    public void publishWill(IOTClient iotClient) {

        log.debug(" publishWill : client : {} may have lost connectivity.", iotClient);

        //Publish will before handling other

        Observable<PublishMessage> willMessageObservable = getDatastore().getWill(iotClient);

        willMessageObservable.subscribe(
                willMessage -> {


                    log.debug(" publishWill : -----------------------------------------------------");
                    log.debug(" publishWill : -------  We have a will {} -------", willMessage);
                    log.debug(" publishWill : -----------------------------------------------------");

                   willMessage = iotClient.copyTransmissionData(willMessage);

                    try {


                        if (willMessage.getQos() > 0) {
                            willMessage.setIsRelease(false);
                            //Save the message as we proceed.
                            willMessage.setMessageId(PublishMessage.ID_TO_FORCE_GENERATION_ON_SAVE);
                            Map.Entry<Long, IotMessageKey> messageIdentity = getDatastore().saveMessage(willMessage).toBlocking().single();
                            willMessage.setMessageId(messageIdentity.getValue().getMessageId());
                        }

                        getMessenger().publish(iotClient.getPartitionId(), willMessage);


                    } catch (RetriableException e) {
                        log.error(" publishWill : experienced issues publishing will.", e);
                    }


                }, throwable -> {
                    if(!(throwable instanceof DoesNotExistException)){
                        log.error(" dirtyDisconnect : problems getting will ", throwable);
                    }

                    disconnectClient(iotClient);
                }, ()-> disconnectClient(iotClient)
        );


    }

    private void disconnectClient(IOTClient iotClient){

        //Notify the server to remove this client from further sending in requests.
        DisconnectMessage disconnectMessage = DisconnectMessage.from(false);
        disconnectMessage = iotClient.copyTransmissionData(disconnectMessage);
        pushToServer(disconnectMessage);


        // Unsubscribe all
        if (iotClient.getIsCleanSession()) {


            Observable<IotSubscription> subscriptionObservable = getDatastore().getSubscriptions(iotClient);

            subscriptionObservable.subscribe(
                    subscription ->
                            getMessenger().unSubscribe(subscription)

                    , throwable -> log.error(" onStop : problems while unsubscribing", throwable)

                    , () -> {

                        Observable<PublishMessage> publishMessageObservable = getDatastore().getMessages(iotClient);
                        publishMessageObservable.subscribe(
                                getDatastore()::removeMessage,
                                throwable -> {
                                    log.error(" onStop : problems while unsubscribing", throwable);
                                    // any way still delete it from our db
                                },
                                () -> {
                                    // and delete it from our db
                                });

                    }
            );
        }

    }


    /**
     * Internal method to handle all activities related to ensuring the worker routes
     * responses or new messages to the server for connected devices to receive their messages.
     *
     * @param iotMessage
     */
    public final void pushToServer(IOTMessage iotMessage){

        log.debug(" pushToServer : sending to client {}", iotMessage);

        getServerRouter().route(iotMessage.getCluster(), iotMessage.getNodeId(), iotMessage);

    }

    @Override
    public int compareTo(BaseSystemHandler baseSystemHandler) {

        if(null == baseSystemHandler)
            throw new NullPointerException("You can't compare a null object.");

        if(baseSystemHandler instanceof Worker)
            return 0;
        else if(baseSystemHandler instanceof Server)
            return 1;
        else
            return -1;
    }
}
