package org.apache.dubbo.registry.eureka.configuration;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author yunxiyi
 * @date 2018/7/21
 */
public class DubboDiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(DubboDiscoveryClient.class);

    private Field eurekaTransportField;

    private DiscoveryClient delegate;

    private AtomicReference<EurekaHttpClient> registrationHttpClient = new AtomicReference<>();

    private AtomicReference<EurekaHttpClient> queryHttpClient = new AtomicReference<>();

    private ApplicationInfoManager cacheApplicationInfo;

    private int maxRetryTimes = 3;

    public DubboDiscoveryClient(EurekaClient eurekaClient,
                                HealthCheckHandler healthCheckHandler) {
        this.delegate = (DiscoveryClient) eurekaClient;
        this.delegate.registerHealthCheck(healthCheckHandler);
        this.eurekaTransportField = ReflectionUtils.findField(DiscoveryClient.class, "eurekaTransport");
        ReflectionUtils.makeAccessible(this.eurekaTransportField);
    }

    private EurekaHttpClient registrationHttpClient() {
        if (this.registrationHttpClient.get() == null) {
            try {
                Object eurekaTransport = this.eurekaTransportField.get(delegate);
                Field registrationClientField = ReflectionUtils.findField(eurekaTransport.getClass(), "registrationClient");
                ReflectionUtils.makeAccessible(registrationClientField);
                this.registrationHttpClient.compareAndSet(null, (EurekaHttpClient) registrationClientField.get(eurekaTransport));
            } catch (IllegalAccessException e) {
                log.error("error getting EurekaHttpClient", e);
            }
        }
        return this.registrationHttpClient.get();
    }

    private EurekaHttpClient queryHttpClient() {
        if (this.queryHttpClient.get() == null) {
            try {
                Object eurekaTransport = this.eurekaTransportField.get(delegate);
                Field queryClientField = ReflectionUtils.findField(eurekaTransport.getClass(), "queryClient");
                ReflectionUtils.makeAccessible(queryClientField);
                this.queryHttpClient.compareAndSet(null, (EurekaHttpClient) queryClientField.get(eurekaTransport));
            } catch (IllegalAccessException e) {
                log.error("error getting EurekaHttpClient", e);
            }
        }
        return this.queryHttpClient.get();
    }

    /**
     * register export service
     * make sure eureka server has same info with local
     *
     * @param registerKey register unique key in current application
     * @param registerUrl register url
     */
    public void register(String registerKey, String registerUrl) {
        InstanceInfo info = getCacheApplicationInfo().getInfo();
        info.getMetadata().put(registerKey, registerUrl);
        info.setStatus(InstanceInfo.InstanceStatus.UP);

        if (register(info, registerKey)) {
            String registerLog = "register eureka server"
                    + " success, has register to url : "
                    + info.getMetadata().get(registerKey);
            log.info(registerLog);
            return;
        }

        String registerLog = "register eureka server fail, "
                + "exceed the max retry times limit,"
                + "current register url is : "
                + info.getMetadata().get(registerKey);
        throw new IllegalStateException(registerLog);
    }

    public void unregister(String registeredKey) {
        if (getCacheApplicationInfo() != null) {
            InstanceInfo info = getCacheApplicationInfo().getInfo();
            String unregisterUrl = info.getMetadata().remove(registeredKey);
            if (unregister(info, registeredKey)) {
                String registerLog = "unregister eureka "
                        + "server success,remove url is:"
                        + unregisterUrl;
                log.info(registerLog);
                return;
            }

            String registerLog = "unregister eureka server fail,"
                    + " unregister exceed the max retry times limit, "
                    + " current unregister url is : " + unregisterUrl;

            throw new IllegalStateException(registerLog);
        }
    }

    private boolean register(InstanceInfo info, String registerKey) {
        for (int retryTimes = 0; retryTimes < maxRetryTimes; retryTimes++) {
            InstanceInfo remoteInstance = queryHttpClient().getInstance(info.getId()).getEntity();
            Map<String, String> remoteRegistry = remoteInstance.getMetadata();
            if (remoteRegistry != null
                    && remoteRegistry.keySet().contains(registerKey)) {
                return true;
            }
            registrationHttpClient().register(info);
        }
        return false;
    }

    private boolean unregister(InstanceInfo info, String registerKey) {
        for (int retryTimes = 0; retryTimes < maxRetryTimes; retryTimes++) {
            InstanceInfo remoteInstance = queryHttpClient().getInstance(info.getId()).getEntity();
            Map<String, String> remoteRegistry = remoteInstance.getMetadata();
            if (remoteRegistry == null
                    || !remoteRegistry.keySet().contains(registerKey)) {
                return true;
            }
            registrationHttpClient().register(info);
        }
        return false;
    }

    public List<URL> query(String subscribeQueryKey) {
        Applications remoteApplication = queryHttpClient().getApplications().getEntity();
        List<Application> registeredApplications = remoteApplication.getRegisteredApplications();
        if (CollectionUtils.isEmpty(registeredApplications)) {
            registeredApplications = delegate.getApplications().getRegisteredApplications();
        }

        List<URL> urls = new ArrayList<>();
        for (Application application : registeredApplications) {
            List<InstanceInfo> instances = application.getInstances();
            if (CollectionUtils.isEmpty(instances)) {
                instances = application.getInstancesAsIsFromEureka();
            }
            urls.addAll(queryExportedUrls(subscribeQueryKey, instances));
        }
        return urls;
    }

    private Set<URL> queryExportedUrls(String subscribeQueryKey, List<InstanceInfo> instances) {
        Set<URL> result = new HashSet<>();
        if (CollectionUtils.isEmpty(instances)) {
            return result;
        }
        for (InstanceInfo info : instances) {
            Map<String, String> registeredInfo = info.getMetadata();
            if (info.getStatus() != InstanceInfo.InstanceStatus.UP
                    || CollectionUtils.isEmpty(registeredInfo)) {
                continue;
            }
            result.addAll(getMatchValues(registeredInfo, subscribeQueryKey));
        }
        return result;
    }

    private Set<URL> getMatchValues(Map<String, String> registeredInfo, String queryKey) {
        Set<URL> exportedUrls = new HashSet<>();
        for (Map.Entry<String, String> registered : registeredInfo.entrySet()) {
            if (StringUtils.startsWithIgnoreCase(registered.getKey(), queryKey)) {
                try {
                    exportedUrls.add(URL.valueOf(registered.getValue()));
                } catch (Exception e) {
                    log.error("It's cant't be convert to url " + registered.getValue(), e);
                }
            }
        }
        return exportedUrls;
    }

    public boolean isAvailable() {
        return delegate.getInstanceRemoteStatus() == InstanceInfo.InstanceStatus.UP;
    }

    private ApplicationInfoManager getCacheApplicationInfo() {
        if (cacheApplicationInfo == null) {
            cacheApplicationInfo = SpringContextHandler.getBean(ApplicationInfoManager.class);
        }
        return cacheApplicationInfo;
    }
}
