package org.apache.dubbo.registry.eureka.configuration;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.EurekaClient;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author yunxiyi
 * @date 2018/7/21
 */
@Configuration
@ConditionalOnProperty(value = "eureka.client.enabled", matchIfMissing = true)
@AutoConfigureBefore({EurekaClientAutoConfiguration.class})
public class DubboEurekaConfiguration {

    private final static String defaultAddress = "eureka://localhost:8761";

    @Bean
    public DubboEurekaHealthCheckHandler dubboEurekaHealthCheckHandler() {
        return new DubboEurekaHealthCheckHandler();
    }

    @Bean
    @ConditionalOnBean(ApplicationInfoManager.class)
    public ApplicationInfoManager.StatusChangeListener statusChangeListener(ApplicationInfoManager infoManager) {
        DubboStatusChangeListener statusChangeListener = new DubboStatusChangeListener();
        infoManager.registerStatusChangeListener(statusChangeListener);
        return statusChangeListener;
    }

    @Bean
    @ConditionalOnMissingBean(DubboDiscoveryClient.class)
    public DubboDiscoveryClient dubboDiscoveryClient(EurekaClient eurekaClient,
                                                     DubboEurekaHealthCheckHandler healthCheckHandler) {
        return new DubboDiscoveryClient(eurekaClient, healthCheckHandler);
    }

    @Bean
    @ConditionalOnMissingBean(ReferenceAnnotationBeanPostProcessor.class)
    public ReferenceAnnotationBeanPostProcessor beanPostProcessor() {
        return new ReferenceAnnotationBeanPostProcessor();
    }

    @Bean("eurekaRegistryConfig")
    public RegistryConfig registryConfig(EurekaClientConfigBean eurekaClientConfigBean) {
        String address;
        try {
            String defaultZone = eurekaClientConfigBean.getServiceUrl().get("defaultZone");
            URL eurekaUrl = new URL(defaultZone);
            address = "eureka://" + eurekaUrl.getAuthority();
        } catch (MalformedURLException e) {
            address = defaultAddress;
        }
        return new RegistryConfig(address);
    }
}
