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

package com.caricah.iotracah.datastore.ignitecache.internal.impl;

import com.caricah.iotracah.core.worker.state.Constant;
import com.caricah.iotracah.core.worker.state.models.SubscriptionFilter;
import com.caricah.iotracah.datastore.ignitecache.internal.AbstractHandler;
import com.caricah.iotracah.exceptions.UnRetriableException;
import org.apache.commons.configuration.Configuration;
import org.apache.ignite.configuration.CacheConfiguration;
import rx.Observable;
import rx.Subscriber;

import javax.cache.Cache;
import java.io.Serializable;
import java.util.*;

/**
 * @author <a href="mailto:bwire@caricah.com"> Peter Bwire </a>
 * @version 1.0 9/20/15
 */
public class SubscriptionFilterHandler extends AbstractHandler<SubscriptionFilter> {

    public static final String CONFIG_IGNITECACHE_SUBSCRIPTION_FILTER_CACHE_NAME = "config.ignitecache.subscription.filter.cache.name";
    public static final String CONFIG_IGNITECACHE_SUBSCRIPTION_FILTER_CACHE_NAME_VALUE_DEFAULT = "iotracah_subscription_filter_cache";


    @Override
    public void configure(Configuration configuration) {


        String cacheName = configuration.getString(CONFIG_IGNITECACHE_SUBSCRIPTION_FILTER_CACHE_NAME, CONFIG_IGNITECACHE_SUBSCRIPTION_FILTER_CACHE_NAME_VALUE_DEFAULT);
        setCacheName(cacheName);

    }

    public Observable<SubscriptionFilter> matchTopicFilterTree(String partition, List<String> topicNavigationRoute) {

        return Observable.create(observer -> {

            Set<Serializable> topicFilterKeys = new HashSet<>();

            ListIterator<String> pathIterator = topicNavigationRoute.listIterator();

            List<String> growingTitles = new ArrayList<>();

            try {

                while (pathIterator.hasNext()) {

                    String name = pathIterator.next();

                    List<String> slWildCardList = new ArrayList<>(growingTitles);

                    if(pathIterator.hasNext()){
                        //We deal with wildcard.
                        slWildCardList.add(Constant.SINGLE_LEVEL_WILDCARD);
                        topicFilterKeys.add(SubscriptionFilter.quickCheckIdKey(partition, slWildCardList));

                    }else{
                        //we deal with full topic
                        slWildCardList.add(name);
                    }

                    List<String> reverseSlWildCardList = new ArrayList<>(slWildCardList);

                    growingTitles.add(name);

                    int sizeOfTopic = slWildCardList.size();
                    if(sizeOfTopic > 1) {
                        sizeOfTopic -= 1;

                        for (int i = 0; i < sizeOfTopic; i++) {

                            if (i >= 0) {
                                slWildCardList.set(i, Constant.MULTI_LEVEL_WILDCARD);
                                reverseSlWildCardList.set(sizeOfTopic - i, Constant.MULTI_LEVEL_WILDCARD);

                                topicFilterKeys.add( SubscriptionFilter.quickCheckIdKey(partition, slWildCardList));
                                topicFilterKeys.add(SubscriptionFilter.quickCheckIdKey(partition, reverseSlWildCardList));

                            }
                        }
                    }


                }

                topicFilterKeys.add( SubscriptionFilter.quickCheckIdKey(partition, growingTitles));

                getBySet(topicFilterKeys).toBlocking().forEach(observer::onNext);


                observer.onCompleted();


            } catch (Exception e) {
                observer.onError(e);
            }

        });


    }


    public Observable<SubscriptionFilter> getTopicFilterTree(String partition, List<String> topicFilterTreeRoute) {

        return Observable.create(observer -> {

            List<String> collectingParentIdList = new ArrayList<>();
            collectingParentIdList.add(SubscriptionFilter.getPartitionAsInitialParentId(partition));

            ListIterator<String> pathIterator = topicFilterTreeRoute.listIterator();

            try {

                while (pathIterator.hasNext()) {

                    String name = pathIterator.next();

                    List<String> parentIdList = new ArrayList<>(collectingParentIdList);
                    collectingParentIdList.clear();

                    for (String parentId : parentIdList) {

                        if (Constant.MULTI_LEVEL_WILDCARD.equals(name)) {

                            getMultiLevelWildCard(observer, partition, parentId);
                        } else if (Constant.SINGLE_LEVEL_WILDCARD.equals(name)) {

                            String query = "partition = ? AND parentId = ? ";
                            Object[] params = {partition, parentId};

                            getByQuery(SubscriptionFilter.class, query, params)
                                    .toBlocking().forEach(subscriptionFilter -> {

                                if (pathIterator.hasNext()) {
                                    try {
                                        collectingParentIdList.add((String) subscriptionFilter.generateIdKey());
                                    } catch (UnRetriableException e) {
                                        log.error(" getTopicFilterTree : error getting subscription filter id", e);
                                    }
                                } else {
                                    observer.onNext(subscriptionFilter);
                                }

                            });

                        } else {


                            String query = "partition = ? AND parentId = ? AND name = ? ";
                            Object[] params = new Object[]{partition, parentId, name};

                            getByQuery(SubscriptionFilter.class, query, params)
                                    .toBlocking().forEach(subscriptionFilter -> {

                                if (pathIterator.hasNext()) {
                                    try {
                                        collectingParentIdList.add((String) subscriptionFilter.generateIdKey());
                                    } catch (UnRetriableException e) {
                                        log.error(" getTopicFilterTree : error getting subscription filter id", e);
                                    }
                                } else {
                                    observer.onNext(subscriptionFilter);
                                }

                            });

                        }
                    }

                }

                observer.onCompleted();


            } catch (Exception e) {
                observer.onError(e);
            }

        });

    }

    private void getMultiLevelWildCard(Subscriber<? super SubscriptionFilter> observer, String partition, String parentId) {

        String query = "partition = ? AND parentId = ? ";
        Object[] params = {partition, parentId};

        getByQuery(SubscriptionFilter.class, query, params)
                .toBlocking().forEach(subscriptionFilter -> {

            observer.onNext(subscriptionFilter);

            try {
                getMultiLevelWildCard(observer, partition, (String) subscriptionFilter.generateIdKey());
            } catch (UnRetriableException e) {
                log.error(" getMultiLevelWildCard : problem generating id", e);
            }

        });


    }

    public Observable<SubscriptionFilter> createTree(String partition, List<String> topicFilterTreeRoute) {

        return Observable.create(observer -> {

                    try {
                        String currentTreeName = "";
                        SubscriptionFilter activeSubscriptionFilter = null;

                        ListIterator<String> pathIterator = topicFilterTreeRoute.listIterator();
                        String parentId = SubscriptionFilter.getPartitionAsInitialParentId(partition);

                        while (pathIterator.hasNext()) {

                            String name;
                            if (!pathIterator.hasPrevious()) {
                                name = pathIterator.next();
                                currentTreeName = name;
                            } else {
                                name = pathIterator.next();
                                currentTreeName += Constant.PATH_SEPARATOR + name;
                            }


                            String query = "partition = ? AND parentId = ? AND name = ? ";
                            Object[] params = {partition, parentId, name};

                            SubscriptionFilter internalSubscriptionFilter = getByQuery(SubscriptionFilter.class, query, params).toBlocking().singleOrDefault(null);

                            if (null == internalSubscriptionFilter) {
                                internalSubscriptionFilter = new SubscriptionFilter();
                                internalSubscriptionFilter.setPartition(partition);
                                internalSubscriptionFilter.setParentId(parentId);
                                internalSubscriptionFilter.setFullTreeName(currentTreeName);
                                internalSubscriptionFilter.setName(name);
                                save(internalSubscriptionFilter);

                                }


                            if (!pathIterator.hasNext()) {
                                activeSubscriptionFilter = internalSubscriptionFilter;
                            } else {
                                parentId = (String) internalSubscriptionFilter.generateIdKey();
                            }
                        }

                        if (Objects.nonNull( activeSubscriptionFilter)) {
                            observer.onNext(activeSubscriptionFilter);
                        }
                        observer.onCompleted();
                    } catch (Exception e) {
                        observer.onError(e);
                    }

                }
        );
    }


}
