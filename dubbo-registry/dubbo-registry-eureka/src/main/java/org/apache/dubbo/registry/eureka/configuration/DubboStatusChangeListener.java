package org.apache.dubbo.registry.eureka.configuration;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.StatusChangeEvent;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yunxiyi
 * @date 2018/7/21
 */
public class DubboStatusChangeListener implements ApplicationInfoManager.StatusChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(DubboStatusChangeListener.class);

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    private static final String PREFIX = "statusListener-";

    @Override
    public String getId() {
        int index = ID_GENERATOR.incrementAndGet();
        return PREFIX + index;
    }

    @Override
    public void notify(StatusChangeEvent statusChangeEvent) {
        logger.info("this instance status is change, "
                + statusChangeEvent.getPreviousStatus().name()
                + " to " + statusChangeEvent.getStatus().name());
    }

}
