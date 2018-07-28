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

import java.util.Arrays;
import java.util.List;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.eureka.configuration.DubboDiscoveryClient;
import org.apache.dubbo.registry.eureka.configuration.SpringContextHandler;
import org.apache.dubbo.registry.support.FailbackRegistry;

/**
 * EurekaRegistry
 *
 * @author yunxiyi
 */
public class EurekaRegistry extends FailbackRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(EurekaRegistry.class);

    private DubboDiscoveryClient discoveryClient;

    private String root;

    public EurekaRegistry(URL url) {
        super(url);
        this.root = url.getParameter(Constants.GROUP_KEY, Constants.DEFAULT_DIRECTORY);
        this.discoveryClient = SpringContextHandler.getBean(DubboDiscoveryClient.class);
    }

    @Override
    protected void doRegister(URL url) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("eureka registry start : " + url.toFullString());
        }

        if (Constants.PROVIDER.equals(url.getParameter(Constants.SIDE_KEY))) {
            discoveryClient.register(toRegisterKey(url), url.toFullString());
        } else {
            LOGGER.warn("consumer don't need to register. url : " + url);
        }
    }

    @Override
    protected void doUnregister(URL url) {
        discoveryClient.unregister(toRegisterKey(url));
    }

    @Override
    protected void doSubscribe(final URL url, final NotifyListener listener) {
        List<URL> registeredUrls = discoveryClient.query(toQueryKey(url));
        if (CollectionUtils.isEmpty(registeredUrls)) {
            throw new IllegalStateException("no service can use");
        }
        for (NotifyListener subNotifyListener : Arrays.asList(listener)) {
            notify(url, subNotifyListener, registeredUrls);
        }
    }

    @Override
    protected void doUnsubscribe(URL url, NotifyListener listener) {
        LOGGER.info("eureka don't need to cancel subscribe, " + url);
    }

    private String toRegisterKey(URL url) {
        return toQueryKey(url) + url.hashCode();
    }

    private String toQueryKey(URL url) {
        if (Constants.ANY_VALUE.equals(url.getServiceInterface())) {
            return toRegistryRootDir();
        }
        return toRegistryRootDir() + url.getServiceInterface() + Constants.PATH_SEPARATOR;
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

    @Override
    public boolean isAvailable() {
        return discoveryClient.isAvailable();
    }

}
