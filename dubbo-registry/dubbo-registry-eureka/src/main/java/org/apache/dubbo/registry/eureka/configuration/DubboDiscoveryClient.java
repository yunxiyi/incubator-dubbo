package org.apache.dubbo.registry.eureka.configuration;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaEvent;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yunxiyi
 * @date 2018/7/21
 */
public class DubboDiscoveryClient extends DiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(DubboDiscoveryClient.class);

    private Field eurekaTransportField;

    private EurekaRegistry eurekaRegistry;

    private DubboCacheEurekaEventListener eurekaEventListener;

    private AtomicReference<EurekaHttpClient> eurekaHttpClient = new AtomicReference<>();


    public DubboDiscoveryClient(ApplicationInfoManager applicationInfoManager, EurekaClientConfig config,
                                DiscoveryClientOptionalArgs args, HealthCheckHandler healthCheckHandler) {
        super(applicationInfoManager, config, args);
        registerHealthCheck(healthCheckHandler);

        //listener refresh registry event
        eurekaEventListener = new DubboCacheEurekaEventListener(this);
        registerEventListener(eurekaEventListener);
        eurekaTransportField = ReflectionUtils.findField(DiscoveryClient.class, "eurekaTransport");
        ReflectionUtils.makeAccessible(this.eurekaTransportField);
    }


   private EurekaHttpClient getEurekaHttpClient() {
        if (this.eurekaHttpClient.get() == null) {
            try {
                Object eurekaTransport = this.eurekaTransportField.get(this);
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
     *
     * @param info register into
     */
    public void register(InstanceInfo info) {
        info.setStatus(InstanceInfo.InstanceStatus.UP);
        getEurekaHttpClient().register(info);
    }


    /**
     * this method use to callback when local cache updated
     *
     * @param eurekaRegistry callback object
     */
    public void setEurekaRegistry(EurekaRegistry eurekaRegistry) {
        this.eurekaRegistry = eurekaRegistry;
    }

    /**
     * query registered service by subscribed key
     *
     * @param subscribedService subscribed key
     * @return registered service
     */
    public List<URL> query(String subscribedService) {
        List<Application> registeredApplications = getApplications().getRegisteredApplications();
        List<URL> urls = new ArrayList<>();
        for (Application application : registeredApplications) {
            List<InstanceInfo> instances = application.getInstancesAsIsFromEureka();
            urls.addAll(findService(subscribedService, instances));
        }
        return urls;
    }

    /**
     * {@link EurekaRegistry#doNotify()}
     * dynamic update registry info
     */
    void notifyEurekaRegistry() {
        eurekaRegistry.doNotify();
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
            String exportedUrl = info.getMetadata().get(subscribedService);
            if (StringUtils.isNotEmpty(exportedUrl)) {
                result.add(URL.valueOf(exportedUrl));
            }
        }
        return result;
    }

    static class DubboCacheEurekaEventListener implements EurekaEventListener {

        DubboDiscoveryClient discoveryClient;

        DubboCacheEurekaEventListener(DubboDiscoveryClient discoveryClient) {
            this.discoveryClient = discoveryClient;
        }

        @Override
        public void onEvent(EurekaEvent event) {
            if (event.getClass() == CacheRefreshedEvent.class) {
                // if need, dynamic refresh registry info
                discoveryClient.notifyEurekaRegistry();
            }
        }

    }
}
