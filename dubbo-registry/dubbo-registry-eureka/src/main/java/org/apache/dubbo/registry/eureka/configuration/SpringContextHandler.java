package org.apache.dubbo.registry.eureka.configuration;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author yunxiyi
 * @date 2018/7/21
 */
public class SpringContextHandler implements ApplicationContextInitializer {

    static ApplicationContext applicationContext;

    public static <T> T getBean(Class<T> tClass) {
        return applicationContext.getBean(tClass);
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        SpringContextHandler.applicationContext = applicationContext;
    }
}
