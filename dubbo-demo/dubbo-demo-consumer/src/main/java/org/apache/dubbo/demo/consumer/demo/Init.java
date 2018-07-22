package org.apache.dubbo.demo.consumer.demo;

import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.demo.DemoService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Created by yunxiyi on 2018/7/22.
 */
@Component
public class Init {

    @Reference(check = false, group = "abc", registry = "eureka")
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

}
