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

    private ApplicationInfoManager applicationInfoManager;

    private int maxRetryTimes = 3;

    public DubboDiscoveryClient(EurekaClient eurekaClient,
                                HealthCheckHandler healthCheckHandler) {
        this.delegate = (DiscoveryClient) eurekaClient;
        this.delegate.registerHealthCheck(healthCheckHandler);
        this.eurekaTransportField = ReflectionUtils.findField(DiscoveryClient.class, "eurekaTransport");
        this.applicationInfoManager = SpringContextHandler.getBean(ApplicationInfoManager.class);
        ReflectionUtils.makeAccessible(this.eurekaTransportField);

    }

    public void register(String registerKey, String registerUrl) {
        InstanceInfo info = applicationInfoManager.getInfo();
        info.getMetadata().put(registerKey, registerUrl);
        info.setStatus(InstanceInfo.InstanceStatus.UP);

        for (int retryTimes = 0; retryTimes < maxRetryTimes; retryTimes++) {
            InstanceInfo remoteInstance = queryHttpClient().getInstance(info.getId()).getEntity();
            Map<String, String> registeredInfo = remoteInstance.getMetadata();
            if (registeredInfo != null && registeredInfo.keySet().contains(registerKey)) {
                log.info("register eureka server success, register url : " + registerUrl);
                return;
            }
            registrationHttpClient().register(info);
        }

        throw new IllegalStateException("register eureka server fail, register url is : " + registerUrl);
    }

    public void unregister(String registeredKey) {
        if (applicationInfoManager != null) {
            InstanceInfo info = applicationInfoManager.getInfo();
            String unregisterUrl = info.getMetadata().remove(registeredKey);

            for (int retryTimes = 0; retryTimes < maxRetryTimes; retryTimes++) {
                InstanceInfo remoteInstance = queryHttpClient().getInstance(info.getId()).getEntity();
                Map<String, String> registeredInfo = remoteInstance.getMetadata();
                if (registeredInfo != null && registeredInfo.keySet().contains(registeredKey)) {
                    log.info("unregister eureka server success, unregister url is:" + unregisterUrl);
                    return;
                }
                registrationHttpClient().register(info);
            }

            throw new IllegalStateException("unregister eureka server fail, unregister url is : " + unregisterUrl);
        }
    }

    public List<URL> query(String subscribeQueryKey) {
        Applications remoteApplications = queryHttpClient().getApplications().getEntity();
        List<Application> registeredApplications = remoteApplications.getRegisteredApplications();
        if (CollectionUtils.isEmpty(registeredApplications)) {
            registeredApplications = delegate.getApplications().getRegisteredApplications();
        }

        Set<URL> registeredUrls = new HashSet<>();

        //if this local instance has not exists remote applications
        InstanceInfo localInstance = applicationInfoManager.getInfo();
        if (!isRegisteredApplications(localInstance, remoteApplications)) {
            registeredUrls.addAll(queryUrlsFromInstance(subscribeQueryKey, localInstance));
        }

        for (Application application : registeredApplications) {
            registeredUrls.addAll(queryUrlsFromApplication(subscribeQueryKey, application));
        }

        return new ArrayList<>(registeredUrls);
    }

    private Set<URL> queryUrlsFromApplication(String subscribeQueryKey,
                                              Application application) {
        Set<URL> registeredApplicationUrls = new HashSet<>();
        if (application == null || CollectionUtils.isEmpty(application.getInstances())) {
            return registeredApplicationUrls;
        }
        for (InstanceInfo info : application.getInstances()) {
            if (info != null && info.getStatus() == InstanceInfo.InstanceStatus.UP) {
                registeredApplicationUrls.addAll(queryUrlsFromInstance(subscribeQueryKey, info));
            }
        }
        return registeredApplicationUrls;
    }

    private Set<URL> queryUrlsFromInstance(String queryKey, InstanceInfo instance) {
        Set<URL> registeredInstanceUrls = new HashSet<>();
        if (CollectionUtils.isEmpty(instance.getMetadata())) {
            return registeredInstanceUrls;
        }
        for (Map.Entry<String, String> registered : instance.getMetadata().entrySet()) {
            if (StringUtils.startsWithIgnoreCase(registered.getKey(), queryKey)) {
                try {
                    registeredInstanceUrls.add(URL.valueOf(registered.getValue()));
                } catch (Exception e) {
                    log.error("It's cant't be convert to url " + registered.getValue(), e);
                }
            }
        }
        return registeredInstanceUrls;
    }

    private boolean isRegisteredApplications(InstanceInfo instance, Applications remoteApplication) {
        return null != remoteApplication.getRegisteredApplications(instance.getAppName());
    }

    public boolean isAvailable() {
        return delegate.getInstanceRemoteStatus() == InstanceInfo.InstanceStatus.UP;
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

}
