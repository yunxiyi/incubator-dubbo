package org.apache.dubbo.registry.eureka.configuration;

import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;

/**
 *
 * @author yunxiyi
 * @date 2018/7/21
 */
public class DubboEurekaHealthCheckHandler implements HealthCheckHandler {

    @Override
    public InstanceInfo.InstanceStatus getStatus(InstanceInfo.InstanceStatus currentStatus) {
        return currentStatus;
    }

}
