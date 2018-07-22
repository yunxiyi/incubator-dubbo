package org.apache.dubbo.registry.eureka.configuration;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.cloud.netflix.eureka.InstanceInfoFactory;
import org.springframework.cloud.netflix.eureka.config.EurekaClientConfigServerAutoConfiguration;
import org.springframework.cloud.netflix.eureka.config.EurekaDiscoveryClientConfigServiceAutoConfiguration;
import org.springframework.cloud.netflix.ribbon.eureka.RibbonEurekaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author yunxiyi
 * @date 2018/7/21
 */
@Configuration
@EnableConfigurationProperties({EurekaClientConfigBean.class, DubboEurekaInstance.class})
@ConditionalOnProperty(value = "eureka.client.enabled", matchIfMissing = true)
@AutoConfigureBefore({EurekaClientAutoConfiguration.class, EurekaClientConfigServerAutoConfiguration.class,
        RibbonEurekaAutoConfiguration.class, EurekaDiscoveryClientConfigServiceAutoConfiguration.class})
public class DubboEurekaConfiguration {

    private final static String defaultAddress = "eureka://localhost:8761";

    @Bean
    @ConditionalOnMissingBean(value = DiscoveryClient.DiscoveryClientOptionalArgs.class, search = SearchStrategy.CURRENT)
    public DiscoveryClient.DiscoveryClientOptionalArgs discoveryClientOptionalArgs() {
        return new DiscoveryClient.DiscoveryClientOptionalArgs();
    }

    @Bean
    @ConditionalOnMissingBean(value = HealthCheckHandler.class, search = SearchStrategy.CURRENT)
    public HealthCheckHandler healthCheckHandler() {
        return new DubboEurekaHealthCheckHandler();
    }

    @Bean
    @ConditionalOnMissingBean(value = ApplicationInfoManager.class, search = SearchStrategy.CURRENT)
    public ApplicationInfoManager applicationInfoManager(EurekaInstanceConfig config) {
        InstanceInfo instanceInfo = new InstanceInfoFactory().create(config);
        ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(config, instanceInfo);
        applicationInfoManager.registerStatusChangeListener(new DubboStatusChangeListener());
        return applicationInfoManager;
    }


    @Bean
    public DubboDiscoveryClient eurekaClient(ApplicationInfoManager applicationInfoManager, EurekaClientConfigBean config,
                                             DiscoveryClient.DiscoveryClientOptionalArgs optionalArgs,
                                             HealthCheckHandler healthCheckHandler) {
        return new DubboDiscoveryClient(applicationInfoManager, config, optionalArgs, healthCheckHandler);
    }

    @Bean
    @ConditionalOnBean({ApplicationInfoManager.class, DubboDiscoveryClient.class, HealthCheckHandler.class})
    public SpringContextHandler contextHandler() {
        return new SpringContextHandler();
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
