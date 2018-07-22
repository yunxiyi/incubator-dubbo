/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.registry.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.EurekaEvent;
import com.netflix.discovery.EurekaEventListener;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.eureka.configuration.DubboDiscoveryClient;
import org.apache.dubbo.registry.eureka.configuration.SpringContextHandler;
import org.apache.dubbo.registry.support.FailbackRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EurekaRegistry
 *
 * @author yunxiyi
 */
public class EurekaRegistry extends FailbackRegistry implements EurekaEventListener {

    private final static Logger logger = LoggerFactory.getLogger(EurekaRegistry.class);

    private DubboDiscoveryClient discoveryClient;

    private ApplicationInfoManager applicationInfoManager;

    public EurekaRegistry(URL url) {
        super(url);
        discoveryClient = SpringContextHandler.getBean(DubboDiscoveryClient.class);
        applicationInfoManager = SpringContextHandler.getBean(ApplicationInfoManager.class);
        discoveryClient.registerEventListener(this);
    }

    @Override
    public boolean isAvailable() {
        return discoveryClient.getInstanceRemoteStatus() == InstanceInfo.InstanceStatus.UP;
    }

    @Override
    public void destroy() {
        super.destroy();
        discoveryClient.shutdown();
    }

    @Override
    protected void doRegister(URL url) {
        if (logger.isDebugEnabled()) {
            logger.debug("eureka registry start : " + url.toFullString());
        }

        InstanceInfo instanceInfo = applicationInfoManager.getInfo();
        if (Constants.PROVIDER.equals(url.getParameter(Constants.SIDE_KEY))) {
            instanceInfo.getMetadata().put(toKey(url), url.toFullString());
        }
        discoveryClient.register(instanceInfo);
    }

    @Override
    protected void doUnregister(URL url) {
        discoveryClient.shutdown();
    }

    @Override
    protected void doSubscribe(final URL url, final NotifyListener listener) {
        List<URL> urls = discoveryClient.query(toKey(url));
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }
        for (NotifyListener nl : Arrays.asList(listener)) {
            notify(url, nl, urls);
        }
    }

    @Override
    protected void doUnsubscribe(URL url, NotifyListener listener) {

    }

    private String toKey(URL url) {
        String serviceInterface = url.getServiceInterface();
        String group = url.getParameter(Constants.GROUP_KEY);
        StringBuilder key = new StringBuilder();

        if (!StringUtils.isBlank(group)) {
            key.append(group).append(".");
        }
        key.append(serviceInterface);
        return key.toString();
    }

    @Override
    public void onEvent(EurekaEvent event) {
        if (event.getClass() == CacheRefreshedEvent.class) {
            // if need, dynamic refresh registry info
            notifySubscriber();
        }
    }

    private void notifySubscriber() {
        Map<URL, Set<NotifyListener>> subscribed = getSubscribed();
        for (Map.Entry<URL, Set<NotifyListener>> subscribe : subscribed.entrySet()) {
            Set<NotifyListener> listeners = subscribe.getValue();
            for (NotifyListener nl : listeners) {
                List<URL> serviceUrls = discoveryClient.query(toKey(subscribe.getKey()));
                doNotifySubscriber(subscribe.getKey(), nl, serviceUrls);
            }
        }
    }

    private void doNotifySubscriber(final URL url, final NotifyListener nl, List<URL> urls) {
        // there will be update
        Map<String, List<URL>> serviceUrls = getNotified().get(url);
        List<URL> result = new ArrayList<>();

        for (URL u : urls) {
            if (serviceUrls == null) {
                result.addAll(urls);
                break;
            }
            for (List<URL> notified : serviceUrls.values()) {
                if (!notified.contains(u)) {
                    result.add(u);
                }
            }
        }
        if (CollectionUtils.isNotEmpty(result)) {
            notify(url, nl, result);
        }
    }
}
