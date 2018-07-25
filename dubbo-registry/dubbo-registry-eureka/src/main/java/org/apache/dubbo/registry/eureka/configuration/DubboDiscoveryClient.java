package org.apache.dubbo.registry.eureka.configuration;

import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.registry.eureka.EurekaRegistry;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yunxiyi
 * @date 2018/7/21
 */
public class DubboDiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(DubboDiscoveryClient.class);

    private Field eurekaTransportField;

    private DiscoveryClient delegate;

    private AtomicReference<EurekaHttpClient> eurekaHttpClient = new AtomicReference<>();
    private static final int maxRegisterTimes = 3;

    public DubboDiscoveryClient(EurekaClient eurekaClient, HealthCheckHandler healthCheckHandler) {
        this.delegate = (DiscoveryClient) eurekaClient;
        this.delegate.registerHealthCheck(healthCheckHandler);
        this.eurekaTransportField = ReflectionUtils.findField(DiscoveryClient.class, "eurekaTransport");
        ReflectionUtils.makeAccessible(this.eurekaTransportField);
    }


    private EurekaHttpClient getEurekaHttpClient() {
        if (this.eurekaHttpClient.get() == null) {
            try {
                Object eurekaTransport = this.eurekaTransportField.get(delegate);
                Field registrationClientField = ReflectionUtils.findField(eurekaTransport.getClass(), "registrationClient");
                ReflectionUtils.makeAccessible(registrationClientField);
                this.eurekaHttpClient.compareAndSet(null, (EurekaHttpClient) registrationClientField.get(eurekaTransport));
            } catch (IllegalAccessException e) {
                log.error("error getting EurekaHttpClient", e);
            }
        }
        return this.eurekaHttpClient.get();
    }

    /**
     * register export service
     * make sure eureka server has same info with local
     *
     * @param info register into
     */
    public void register(InstanceInfo info) {
        info.setStatus(InstanceInfo.InstanceStatus.UP);
        int currRegisterTimes = 0;
        while (true) {
            EurekaHttpResponse<InstanceInfo> response = getEurekaHttpClient().getInstance(info.getId());
            Map<String, String> remoteRegistry = response.getEntity().getMetadata();
            Map<String, String> localRegistry = info.getMetadata();
            boolean hasEveryRegistryForRemote = true;
            String unregisterUrl = null;
            for (String registryKey : localRegistry.keySet()) {
                if (registryKey.startsWith(EurekaRegistry.REGISTRY_PREFIX)
                        && !remoteRegistry.containsKey(registryKey)) {
                    hasEveryRegistryForRemote = false;
                    unregisterUrl = localRegistry.get(registryKey);
                    break;
                }
            }
            if (currRegisterTimes >= maxRegisterTimes
                    || hasEveryRegistryForRemote) {
                return;
            }
            log.info("register eureka server, currRegisterTimes : " + currRegisterTimes
                    + ", unregister url : " + unregisterUrl);
            getEurekaHttpClient().register(info);
            currRegisterTimes++;
        }
    }

    /**
     * query registered service by subscribed key
     *
     * @param subscribedService subscribed key
     * @return registered service
     */
    public List<URL> query(String subscribedService) {
        List<Application> registeredApplications = delegate.getApplications().getRegisteredApplications();
        List<URL> urls = new ArrayList<>();
        for (Application application : registeredApplications) {
            List<InstanceInfo> instances = application.getInstances();
            if (CollectionUtils.isEmpty(instances)) {
                instances = application.getInstancesAsIsFromEureka();
            }
            urls.addAll(findService(subscribedService, instances));
        }
        return urls;
    }

    private Set<URL> findService(String subscribedService, List<InstanceInfo> instances) {
        Set<URL> result = new HashSet<>();
        if (CollectionUtils.isEmpty(instances)) {
            return result;
        }
        for (InstanceInfo info : instances) {
            if (info.getStatus() != InstanceInfo.InstanceStatus.UP) {
                continue;
            }
            for (Map.Entry<String, String> entry : info.getMetadata().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
                    continue;
                }
                if (key.startsWith(subscribedService)) {
                    result.add(URL.valueOf(entry.getValue()));
                }
            }
        }
        return result;
    }

    public boolean isAvailable() {
        return delegate.getInstanceRemoteStatus() == InstanceInfo.InstanceStatus.UP;
    }

    public void shutdown() {
        delegate.shutdown();
    }
}
