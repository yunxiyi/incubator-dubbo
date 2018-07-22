package org.apache.dubbo.registry.eureka.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;

import javax.annotation.PostConstruct;

/**
 *
 * @author yunxiyi
 * @date 2018/7/21
 */
@ConfigurationProperties(EurekaClientConfigBean.PREFIX)
public class DubboEurekaClientConfig extends EurekaClientConfigBean {

}
