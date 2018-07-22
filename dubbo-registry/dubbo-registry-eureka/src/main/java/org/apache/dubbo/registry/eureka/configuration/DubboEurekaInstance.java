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
@ConfigurationProperties(prefix = "eureka.instance")
public class DubboEurekaInstance extends EurekaInstanceConfigBean {

    private static final String UNKNOWN = "unknown";

    public DubboEurekaInstance(InetUtils inetUtils) {
        super(inetUtils);
    }

    @PostConstruct
    public void init() {
        String appname = getAppname();
        if (StringUtils.isBlank(appname) || UNKNOWN.equals(appname)) {
            ApplicationConfig application = SpringContextHandler.getBean(ApplicationConfig.class);
            if (application != null && StringUtils.isNotEmpty(application.getName())) {
                setAppname(application.getName());
            }
        }
    }

}
