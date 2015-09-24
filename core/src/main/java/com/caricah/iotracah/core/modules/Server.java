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

import com.caricah.iotracah.core.worker.state.messages.base.IOTMessage;
import com.caricah.iotracah.core.worker.state.messages.base.Protocal;
import com.caricah.iotracah.core.modules.base.IOTBaseHandler;
import com.caricah.iotracah.system.BaseSystemHandler;
import rx.Subscriber;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:bwire@caricah.com"> Peter Bwire </a>
 * @version 1.0 8/10/15
 */
public abstract class Server<T> extends IOTBaseHandler {

    Map<Protocal, Worker> workerMap = new HashMap<>();


    /**
     * Implementation is expected to transform a server specific message
     * to an internal message that the iotracah workers can handle.
     *
     * Everything that goes beyond the server to workers and eventers
     * or the other way round.
     *
     * @param serverMessage
     * @return
     */
    protected abstract IOTMessage toIOTMessage(T serverMessage);

    /**
     * Implementation transforms the internal message to a server specific message
     * that the server now knows how to handle.
     *
     * At the risk of making iotracah create so many unwanted objects,
     * This would be the best way to just ensure the appropriate plugin separation
     * is maintained.
     *
     * @param internalMessage
     * @return
     */
    protected abstract T toServerMessage(IOTMessage internalMessage);


    /**
     * Implementation expected to be called whenever a message is being pushed to
     * workers. This method populates some miscellaneous iotMessage details that are
     * server specific. These data will aid during the process of identification of
     * the return path to the connected device.
     *
     * @param connectionId
     * @param sessionId
     * @param clientId
     * @param message
     *
     */
    public final void pushToWorker(Serializable connectionId, Serializable sessionId, String clientId, T message){

        if(null == message){
            return;
        }

        IOTMessage ioTMessage = toIOTMessage(message);

        //Client specific variables.
        ioTMessage.setConnectionId(connectionId);
        ioTMessage.setSessionId(sessionId);
        ioTMessage.setClientIdentifier(clientId);

        //Hardware specific variables
        ioTMessage.setNodeId(getNodeId());
        ioTMessage.setCluster(getCluster());
        ioTMessage.setProtocal(getProtocal());

        workerMap.get(ioTMessage.getProtocal()).onNext(ioTMessage);

    }

    public void dirtyDisconnect(Serializable connectionId, Serializable sessionId, String clientId) {

        //TODO: performs a dirty disconnection for our message.


    }
    @Override
    public void call(Subscriber<? super IOTMessage> subscriber) {

        if(subscriber instanceof Worker){

            Worker worker = (Worker) subscriber;
            workerMap.put(worker.getProtocal(), worker);
        }

        super.call(subscriber);

    }




    @Override
    public int compareTo(BaseSystemHandler baseSystemHandler) {

        if(null == baseSystemHandler)
            throw new NullPointerException("You can't compare a null object.");

        if(baseSystemHandler instanceof Server)
            return 0;
        else
            return -1;
    }


}