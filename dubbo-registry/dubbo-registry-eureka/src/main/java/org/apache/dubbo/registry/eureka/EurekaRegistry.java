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
import java.util.Arrays;
import java.util.List;
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
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.ApplicationListener;

/**
 * EurekaRegistry
 *
 * @author yunxiyi
 */
public class EurekaRegistry extends FailbackRegistry implements
        ApplicationListener<HeartbeatEvent> {

    private final static Logger logger = LoggerFactory.getLogger(EurekaRegistry.class);

    private DubboDiscoveryClient discoveryClient;

    private ApplicationInfoManager applicationInfoManager;

    public EurekaRegistry(URL url) {
        super(url);
        discoveryClient = SpringContextHandler.getBean(DubboDiscoveryClient.class);
        applicationInfoManager = SpringContextHandler.getBean(ApplicationInfoManager.class);
        SpringContextHandler.addListener(this);
    }

    @Override
    public boolean isAvailable() {
        return discoveryClient.isAvailable();
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
        InstanceInfo instanceInfo = applicationInfoManager.getInfo();
        instanceInfo.getMetadata().remove(toKey(url));
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
            key.append(group).append("/");
        }
        key.append(serviceInterface);
        return key.toString();
    }

    /**
     * resolve Heartbeat Event
     *
     * @param event Heartbeat
     */
    @Override
    public void onApplicationEvent(HeartbeatEvent event) {
        //TODO maybe refresh Invoker url parameter
        // if provider's setting is modified. For example,
        // serialization from hession2 changed to fastjson,
        // or protocol from dubbo changed to http.
        // there is nothing to do that consumer wanna,
    }
}
