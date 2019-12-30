package org.freework.zk.web.ui;

import org.freework.zk.web.ui.util.CuratorHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * Session中Curator实例清理监听.
 *
 * @author changhe.yang
 */
@WebListener
public class ZkWebUiCleanupListener implements HttpSessionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZkWebUiCleanupListener.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("session created");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        CuratorHolder.destroyIfNecessary(event.getSession());
    }
}
