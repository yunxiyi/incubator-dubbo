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

        for (int retryTimes = 0; retryTimes < maxRetryTimes; retryTimes++) {
            InstanceInfo remoteInstance = queryHttpClient().getInstance(info.getId()).getEntity();
            Map<String, String> remoteRegistry = remoteInstance.getMetadata();
            if (remoteRegistry != null && remoteRegistry.keySet().contains(registerKey)) {
                log.info("register eureka server success, register url : " + registerUrl);
                return;
            }
            registrationHttpClient().register(info);
        }

        throw new IllegalStateException("exceed the max retry times limit, register url is : " + registerUrl);
    }

    public void unregister(String registeredKey) {
        if (getCacheApplicationInfo() != null) {
            InstanceInfo info = getCacheApplicationInfo().getInfo();
            String unregisterUrl = info.getMetadata().remove(registeredKey);

            for (int retryTimes = 0; retryTimes < maxRetryTimes; retryTimes++) {
                InstanceInfo remoteInstance = queryHttpClient().getInstance(info.getId()).getEntity();
                Map<String, String> remoteRegistry = remoteInstance.getMetadata();
                if (remoteRegistry != null && remoteRegistry.keySet().contains(registeredKey)) {
                    log.info("unregister eureka server success, unregister url is:" + unregisterUrl);
                    return;
                }
                registrationHttpClient().register(info);
            }

            throw new IllegalStateException("exceed the max retry times limit, unregister url is : " + unregisterUrl);
        }
    }

    public List<URL> query(String subscribeQueryKey) {
        Applications remoteApplication = queryHttpClient().getApplications().getEntity();
        List<Application> registeredApplications = remoteApplication.getRegisteredApplications();
        if (CollectionUtils.isEmpty(registeredApplications)) {
            registeredApplications = delegate.getApplications().getRegisteredApplications();
        }

        Application localApplication = getLocalApplication(remoteApplication);
        if (localApplication != null) {
            registeredApplications.add(localApplication);
        }

        Set<URL> urls = new HashSet<>();
        for (Application application : registeredApplications) {
            urls.addAll(queryUrlsFromApplication(subscribeQueryKey, application));
        }

        return new ArrayList<>(urls);
    }

    private Set<URL> queryUrlsFromApplication(String subscribeQueryKey,
                                              Application application) {
        Set<URL> result = new HashSet<>();
        if (application == null || CollectionUtils.isEmpty(application.getInstances())) {
            return result;
        }
        for (InstanceInfo info : application.getInstances()) {
            if (info != null && info.getStatus() == InstanceInfo.InstanceStatus.UP) {
                result.addAll(queryUrlsFromInstance(subscribeQueryKey, info));
            }
        }
        return result;
    }

    private Set<URL> queryUrlsFromInstance(String queryKey, InstanceInfo instance) {
        Set<URL> exportedUrls = new HashSet<>();
        if (CollectionUtils.isEmpty(instance.getMetadata())) {
            return exportedUrls;
        }
        for (Map.Entry<String, String> registered : instance.getMetadata().entrySet()) {
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

    private Application getLocalApplication(Applications remoteApplication) {
        // local registry info
        InstanceInfo currentInstance = getCacheApplicationInfo().getInfo();
        Application currentApplication = remoteApplication.getRegisteredApplications(currentInstance.getAppName());
        if (currentApplication == null) {
            // not sync to registered applications
            return queryHttpClient().getApplication(currentInstance.getAppName()).getEntity();
        }
        return null;
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
