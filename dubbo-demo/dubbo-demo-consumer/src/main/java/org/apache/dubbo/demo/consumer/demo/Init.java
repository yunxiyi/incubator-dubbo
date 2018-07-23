package org.apache.dubbo.demo.consumer.demo;

import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.discovery.DiscoveryClient;
import javax.annotation.PostConstruct;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.registry.eureka.configuration.DubboDiscoveryClient;
import org.apache.dubbo.registry.eureka.configuration.DubboEurekaConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by yunxiyi on 2018/7/22.
 */
@Configuration
@ImportAutoConfiguration(DubboEurekaConfiguration.class)
public class Init {

    @Reference(check = false, group = "abc", registry = "eureka")
    DemoService demoService;

    @Bean
    public DubboDiscoveryClient dubboDiscoveryClient(DiscoveryClient discoveryClient,
                                                     HealthCheckHandler healthCheckHandler) {
        return new DubboDiscoveryClient(discoveryClient, healthCheckHandler);
    }

    @PostConstruct
    public void init() {
        while (true) {
            try {
                Thread.sleep(1000);
                String hello = demoService.sayHello("world");
                System.out.println(hello);

            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

}
