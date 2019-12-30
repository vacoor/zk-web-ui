package org.freework.zk.web.ui.util;

import com.google.common.collect.Maps;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * CuratorHolder.
 *
 * @author changhe.yang
 */
public class CuratorHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(CuratorHolder.class);
    private static final String ZK_INSTANCES_KEY = "zk.instances";

    private CuratorHolder() {
        throw new AssertionError("can't instantiate");
    }

    /**
     * 如果当前session没有对应的 curator 实例则创建, 否则直接返回.
     *
     * @param session the http session
     * @param zkUrl   the zookeeper url
     * @return the curator instance
     */
    @SuppressWarnings("unchecked")
    public static CuratorFramework createIfNecessary(final HttpSession session, final String zkUrl) {
        final String serverUrl = zkUrl.startsWith("zookeeper://") ? zkUrl.substring(12) : zkUrl;
        Map<String, Object> instanceMap = (Map<String, Object>) session.getAttribute(ZK_INSTANCES_KEY);
        if (null == instanceMap) {
            instanceMap = Maps.newHashMap();
            session.setAttribute(ZK_INSTANCES_KEY, instanceMap);
        }

        CuratorFramework instance;
        final Object value = instanceMap.get(serverUrl);
        if (!(value instanceof CuratorFramework)) {
            final CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                    .connectString(serverUrl)
                    // .retryPolicy(new RetryNTimes(Integer.MAX_VALUE, 1000))
                    .retryPolicy(new RetryNTimes(10, 1000))
                    .connectionTimeoutMs(5000);
            instance = builder.build();
            instance.start();
            instanceMap.put(serverUrl, instance);
        } else {
            instance = (CuratorFramework) value;
        }
        return instance;
    }

    /**
     * 销毁当前session关联的所有curator 实例.
     *
     * @param session the http session
     */
    @SuppressWarnings("unchecked")
    public static void destroyIfNecessary(final HttpSession session) {
        final Map<String, ?> instancesMap = (Map<String, ?>) session.getAttribute(ZK_INSTANCES_KEY);
        if (null == instancesMap) {
            return;
        }
        for (final Map.Entry<String, ?> entry : instancesMap.entrySet()) {
            final Object instance = entry.getValue();
            if (instance instanceof CuratorFramework) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("destroy curator instance: {} on session: {}", instance, session.getId());
                }
                ((CuratorFramework) instance).close();
            } else {
                LOGGER.warn("unknown object({}) found in curator instances map", instance.getClass());
            }
            instancesMap.remove(entry.getKey());
        }
    }
}
