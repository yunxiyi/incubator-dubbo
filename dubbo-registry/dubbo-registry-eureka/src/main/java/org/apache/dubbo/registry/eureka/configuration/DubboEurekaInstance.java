package org.apache.dubbo.registry.eureka.configuration;

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;

import javax.annotation.PostConstruct;

/**
 * @author yunxiyi
 * @date 2018/7/21
 */
public class DubboEurekaInstance {

    private static final String UNKNOWN = "unknown";

    EurekaInstanceConfigBean configBean;

    public DubboEurekaInstance(EurekaInstanceConfigBean configBean) {
        this.configBean = configBean;
    }

    @PostConstruct
    public void init() {
        String appName = configBean.getAppname();
        if (StringUtils.isBlank(appName) || UNKNOWN.equals(appName)) {
            ApplicationConfig application = SpringContextHandler.getBean(ApplicationConfig.class);
            if (application != null && StringUtils.isNotEmpty(application.getName())) {
                configBean.setAppname(application.getName());
            }
        }
    }

}
