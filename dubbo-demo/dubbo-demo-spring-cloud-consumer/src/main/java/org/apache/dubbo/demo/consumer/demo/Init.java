package org.apache.dubbo.demo.consumer.demo;

import javax.annotation.PostConstruct;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.demo.DemoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by yunxiyi on 2018/7/22.
 */
@Configuration
public class Init {

    @Reference(check = false, group = "abc")
    DemoService demoService;

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

    @Bean
    public ApplicationConfig applicationConfig() {
        return new ApplicationConfig("demo-consumer");
    }

}
