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

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.eureka.configuration.DubboDiscoveryClient;
import org.apache.dubbo.registry.eureka.configuration.SpringContextHandler;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * EurekaRegistry
 *
 * @author yunxiyi
 */
public class EurekaRegistry extends FailbackRegistry implements
        ApplicationListener {

    private final static Logger logger = LoggerFactory.getLogger(EurekaRegistry.class);

    private DubboDiscoveryClient discoveryClient;

    private String root;

    public EurekaRegistry(URL url) {
        super(url);
        this.root = url.getParameter(Constants.GROUP_KEY, Constants.DEFAULT_DIRECTORY);
        this.discoveryClient = SpringContextHandler.getBean(DubboDiscoveryClient.class);
        SpringContextHandler.addApplicationListener(this);
    }

    @Override
    protected void doRegister(URL url) {
        if (logger.isDebugEnabled()) {
            logger.debug("eureka registry start : " + url.toFullString());
        }

        if (Constants.PROVIDER.equals(url.getParameter(Constants.SIDE_KEY))) {
            discoveryClient.register(toRegisterKey(url), url.toFullString());
        } else {
            logger.warn("consumer don't need to register. url : " + url);
        }
    }

    @Override
    protected void doUnregister(URL url) {
        discoveryClient.unregister(toRegisterKey(url));
    }

    @Override
    protected void doSubscribe(final URL url, final NotifyListener listener) {
        List<URL> registeredUrls = discoveryClient.query(toQueryKey(url));
        if (Constants.CONSUMER_SIDE.endsWith(url.getParameter(Constants.SIDE_KEY))
                && CollectionUtils.isEmpty(registeredUrls)) {
            throw new IllegalStateException("no service can use");
        }
        for (NotifyListener nl : Arrays.asList(listener)) {
            notify(url, nl, registeredUrls);
        }
    }

    @Override
    protected void doUnsubscribe(URL url, NotifyListener listener) {

    }

    private String toRegisterKey(URL url) {
        StringBuilder registerKey = new StringBuilder()
                .append(toRegistryRootDir())
                .append(url.getServiceInterface())
                .append(Constants.PATH_SEPARATOR)
                .append(url.hashCode());
        return registerKey.toString();
    }

    private String toQueryKey(URL url) {
        if (Constants.ANY_VALUE.equals(url.getServiceInterface())) {
            return toRegistryRootDir();
        }
        StringBuilder queryKey = new StringBuilder()
                .append(toRegistryRootDir())
                .append(url.getServiceInterface())
                .append(Constants.PATH_SEPARATOR);
        return queryKey.toString();
    }

    private String toRegistryRootDir() {
        if (!root.startsWith(Constants.PATH_SEPARATOR)) {
            root = Constants.PATH_SEPARATOR + root;
        }
        if (!root.endsWith(Constants.PATH_SEPARATOR)) {
            root += Constants.PATH_SEPARATOR;
        }
        return root;
    }

    /**
     * resolve Heartbeat Event
     *
     * @param event Heartbeat
     */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        //TODO maybe refresh Invoker url parameter
        // if provider's setting is modified. For example,
        // serialization from hession2 changed to fastjson,
        // or protocol from dubbo changed to http.
        // there is nothing to do that consumer wanna,
    }

    @Override
    public boolean isAvailable() {
        return discoveryClient.isAvailable();
    }

}
