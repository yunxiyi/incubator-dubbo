package org.apache.dubbo.registry.eureka.configuration;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * why use this class to get bean ?
 * because sometimes execute {@link ReferenceBean#getObject()} method
 * before {@link ApplicationContextAware#setApplicationContext(ApplicationContext)}.
 *
 * @author yunxiyi
 * @date 2018/7/21
 */
public class SpringContextHandler implements ApplicationContextInitializer {

    private final static Logger LOGGER = LoggerFactory.getLogger(SpringContextHandler.class);

    static ApplicationContext applicationContext;

    public static <T> T getBean(Class<T> tClass) {
        try {
            return applicationContext.getBean(tClass);
        } catch (Exception e) {
            LOGGER.error(" not found " + tClass.getName() + " bean");
            return null;
        }
    }

    public static void addApplicationListener(ApplicationListener<?> listener) {
        AbstractApplicationContext context = (AbstractApplicationContext) applicationContext;
        context.addApplicationListener(listener);
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        SpringContextHandler.applicationContext = applicationContext;
    }
}
