package org.freework.zk.web.ui;

import freework.codec.Hex;
import freework.io.IOUtils;
import freework.util.Bytes;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.freework.zk.web.ui.util.CuratorHolder;
import org.freework.zk.web.ui.util.Jacksons;
import org.freework.zk.web.ui.util.OrderedProperties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Zookeeper UI servlet.
 *
 * @author changhe.yang
 */
@WebServlet(urlPatterns = "/*")
public class ZookeeperWebUiServlet extends HttpServlet {
    /**
     * UTF-8 charset.
     */
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * ZK internal folder (quota info, etc) - have to stay away from it.
     */
    private static final String ZK_SYSTEM_NODE_PATH = "/zookeeper";

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        final String pathInfo = req.getPathInfo();
        final String zkUrl = req.getHeader("zkUrl");

        // 没有指定路径或没有 zookeeper 地址, 重定向到首页进行输入.
        if (null == pathInfo || ("/".equals(pathInfo) && null == zkUrl)) {
            resp.sendRedirect(req.getContextPath() + req.getServletPath() + "/index.html");
            return;
        }

        // 导出节点操作.
        if (null != req.getParameter("dump")) {
            try {
                final String url = req.getParameter("zkUrl");
                final CuratorFramework zk = CuratorHolder.createIfNecessary(req.getSession(), url);
                final OrderedProperties o = new OrderedProperties();
                o.putAll(asProperties(zk, pathInfo));
                o.store(resp.getWriter(), url);
            } catch (Exception e) {
                throw new ServletException(e);
            }
            return;
        }

        // 静态资源.
        if (pathInfo.endsWith(".html") || pathInfo.endsWith(".css") || pathInfo.endsWith(".js") || pathInfo.startsWith("/images/") || pathInfo.startsWith("/js/")) {
            this.writeResource(pathInfo, resp);
            return;
        }

        // 获取当前节点信息和子节点信息.
        try {
            final CuratorFramework client = CuratorHolder.createIfNecessary(req.getSession(), zkUrl);
            final View[] views = ls(client, pathInfo);
            final String json = Jacksons.serialize(views);
            resp.getWriter().print(json);
        } catch (final Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * 写入静态资源.
     *
     * @param pathInfo     静态资源路径
     * @param httpResponse the http response
     * @throws IOException 如果IO发生异常
     */
    private void writeResource(final String pathInfo, final HttpServletResponse httpResponse) throws IOException {
        final InputStream in = getClass().getClassLoader().getResourceAsStream("support/web" + pathInfo);
        if (null == in) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            httpResponse.setCharacterEncoding("UTF-8");
            if (pathInfo.endsWith(".html")) {
                httpResponse.setContentType("text/html");
            } else if (pathInfo.endsWith(".png")) {
                httpResponse.setContentType("image/png");
            } else if (pathInfo.endsWith(".js")) {
                httpResponse.setContentType("text/javascript");
            } else if (pathInfo.endsWith(".css")) {
                httpResponse.setContentType("text/css");
            }
            IOUtils.flow(in, httpResponse.getOutputStream(), true, false);
        }
    }

    /**
     * 更新节点数据.
     *
     * @param httpRequest  the http request
     * @param httpResponse the http response
     * @throws IOException 如果IO发生异常
     */
    @Override
    protected void doPost(final HttpServletRequest httpRequest, final HttpServletResponse httpResponse) throws IOException {
        final String pathInfo = httpRequest.getPathInfo();
        final String value = httpRequest.getParameter("value");
        final String zkUrl = httpRequest.getHeader("zkUrl");

        final Map<String, Object> ret = new HashMap<String, Object>();
        if (null == pathInfo || null == value) {
            ret.put("success", false);
            ret.put("message", "illegal_request");
        } else {
            try {
                final CuratorFramework zk = CuratorHolder.createIfNecessary(httpRequest.getSession(), zkUrl);
                if (null == zk.checkExists().forPath(pathInfo)) {
                    ret.put("success", false);
                    ret.put("message", "node is not exists");
                } else {
                    zk.setData().forPath(pathInfo, Bytes.toBytes(value));
                    ret.put("success", true);
                    ret.put("message", "node value = '" + value + "'");
                }
            } catch (final Exception e) {
                ret.put("success", false);
                ret.put("message", "internal_error: " + e.getMessage());
            }
        }
        httpResponse.getWriter().print(Jacksons.serialize(ret));
    }

    /**
     * 创建Zookeeper节点.
     *
     * @param httpRequest  the http request
     * @param httpResponse the http response
     * @throws IOException 如果IO发生异常
     */
    @Override
    protected void doPut(final HttpServletRequest httpRequest, final HttpServletResponse httpResponse) throws IOException {
        final String pathInfo = httpRequest.getPathInfo();
        final String type = httpRequest.getParameter("type");
        final String value = httpRequest.getParameter("value");
        final String zkUrl = httpRequest.getHeader("zkUrl");

        final Map<String, Object> ret = new HashMap<String, Object>();
        if (null == pathInfo || null == value) {
            ret.put("success", false);
            ret.put("message", "illegal_request");
        } else {
            CuratorFramework zk = CuratorHolder.createIfNecessary(httpRequest.getSession(), zkUrl);
            try {
                if (null != zk.checkExists().forPath(pathInfo)) {
                    ret.put("success", false);
                    ret.put("message", "node already exists");
                } else {
                    zk.create().withMode("0".equals(type) ? CreateMode.PERSISTENT : CreateMode.EPHEMERAL).forPath(pathInfo, Bytes.toBytes(value));
                    ret.put("success", true);
                    ret.put("message", "node value = '" + value + "'");
                }
            } catch (final Exception e) {
                ret.put("success", false);
                ret.put("message", "internal_error: " + e.getMessage());
            }
        }
        httpResponse.getWriter().print(Jacksons.serialize(ret));
    }

    /**
     * 删除Zookeeper节点.
     *
     * @param httpRequest  the http request
     * @param httpResponse the http response
     * @throws IOException 如果IO发生异常
     */
    @Override
    protected void doDelete(final HttpServletRequest httpRequest, final HttpServletResponse httpResponse) throws IOException {
        final String pathInfo = httpRequest.getPathInfo();
        final Map<String, Object> ret = new HashMap<String, Object>();
        final String zkUrl = httpRequest.getHeader("zkUrl");

        if (null == pathInfo) {
            ret.put("success", false);
            ret.put("message", "illegal_request");
        } else {
            try {
                CuratorFramework zk = CuratorHolder.createIfNecessary(httpRequest.getSession(), zkUrl);
                if (null == zk.checkExists().forPath(pathInfo)) {
                    ret.put("success", false);
                    ret.put("message", "node is not exists");
                } else {
                    zk.delete().forPath(pathInfo);
                    ret.put("success", true);
                    ret.put("message", "node '" + pathInfo + "' is deleted");
                }
            } catch (final Exception e) {
                ret.put("success", false);
                ret.put("message", "internal_error: " + e.getMessage());
            }
        }
        httpResponse.getWriter().print(Jacksons.serialize(ret));
    }

    public static class View implements Comparable<View> {
        private final String path;
        private final String data;

        /**
         * 节点数据的字节数
         */
        private long length;

        /**
         * 节点创建时的zxid.
         */
        private long czxid;

        /**
         * 节点最新一次更新发生时的zxid.
         */
        private long mzxid;

        /**
         * 节点创建时的时间戳.
         */
        private long ctime;

        /**
         * 节点最新一次更新发生时的时间戳.
         */
        private long mtime;

        /**
         * 节点数据的更新次数.
         */
        private int version;

        /**
         * 子节点的更新次数.
         */
        private int cversion;

        /**
         * 节点ACL(授权信息)的更新次数
         */
        private int aversion;

        /**
         * 如果节点为临时节点，那么它的值为这个节点拥有者的session ID；如果该节点不是ephemeral节点, ephemeralOwner值为0.
         */
        private long ephemeralOwner;

        /**
         * 子节点个数.
         */
        private int items;

        public View(final String path, final String data, final long length,
                    final long czxid, final long mzxid, final long ctime,
                    final long mtime, final int version, final int cvertion,
                    final int aversion, final long ephemeralOwner, final int items) {
            this.path = path;
            this.data = data;
            this.length = length;
            this.czxid = czxid;
            this.mzxid = mzxid;
            this.ctime = ctime;
            this.mtime = mtime;
            this.version = version;
            this.cversion = cvertion;
            this.aversion = aversion;
            this.ephemeralOwner = ephemeralOwner;
            this.items = items;
        }

        public String getPath() {
            return path;
        }

        public String getData() {
            return data;
        }

        public long getLength() {
            return length;
        }

        public long getCzxid() {
            return czxid;
        }

        public long getMzxid() {
            return mzxid;
        }

        public long getCtime() {
            return ctime;
        }

        public long getMtime() {
            return mtime;
        }

        public int getVersion() {
            return version;
        }

        public int getCversion() {
            return cversion;
        }

        public int getAversion() {
            return aversion;
        }

        public long getEphemeralOwner() {
            return ephemeralOwner;
        }

        public int getItems() {
            return items;
        }

        @Override
        public int compareTo(final View other) {
            if (this == other) {
                return 0;
            }
            if (ZK_SYSTEM_NODE_PATH.equals(this.path)) {
                return -1;
            }
            if (ZK_SYSTEM_NODE_PATH.equals(other.path)) {
                return 1;
            }
            return this.path.compareTo(other.path);
        }
    }

    private static View stat(final CuratorFramework client, final String path) throws Exception {
        if (null != client.checkExists().forPath(path)) {
            final Stat stat = new Stat();
            final byte[] data = client.getData().storingStatIn(stat).forPath(path);
            final long length = Math.max(null != data ? data.length : 0, stat.getDataLength());
            final String hexData = null != data ? (0 < data.length ? "0x" + Hex.encode(data) : null) : null;
            return new View(
                    path, hexData, length, stat.getCzxid(),
                    stat.getMzxid(), stat.getCtime(), stat.getMtime(), stat.getVersion(),
                    stat.getCversion(), stat.getAversion(), stat.getEphemeralOwner(), stat.getNumChildren()
            );
        }
        return null;
    }

    private static View[] ls(final CuratorFramework client, final String path) throws Exception {
        final List<String> children = null != client.checkExists().forPath(path) ? client.getChildren().forPath(path) : null;
        if (null != children && !children.isEmpty()) {
            final List<View> candidates = new ArrayList<>(children.size());
            for (final String child : children) {
                /*
                if (ZK_SYSTEM_NODE.equals(child)) {
                    continue;
                }
                */

                final View view = stat(client, resolve(path, child));
                if (null != view) {
                    candidates.add(view);
                }
            }
            Collections.sort(candidates);
            return candidates.toArray(new View[candidates.size()]);
        }
        return new View[0];
    }

    private static Map<String, String> asProperties(final CuratorFramework zk, final String... paths) throws Exception {
        final Map<String, String> props = new TreeMap<String, String>();
        for (final String path : paths) {
            asProperties(zk, path, props);
        }
        return props;
    }

    private static void asProperties(final CuratorFramework zk, final String path, final Map<String, String> props) throws Exception {
        final Map<String, String> finalProps = null != props ? props : new TreeMap<String, String>();
        final Stat stat = zk.checkExists().forPath(path);
        if (null == stat || 0 != stat.getEphemeralOwner()) {
            if (null != stat) {
                // skip ephemeral znode
                System.err.println(String.format("skip ephemeral znode: [%s]", path));
            }
            return;
        }

        final byte[] data = zk.getData().forPath(path);
        //path.substring(1).replace('/', '.');
        final String key = path;
        if (!"".equals(key) && null != data) {
            finalProps.put(key, new String(data, UTF_8));
        }

        final List<String> children = zk.getChildren().forPath(path);
        if (null != children && !children.isEmpty()) {
            for (final String child : children) {
                asProperties(zk, resolve(path, child), finalProps);
            }
        }
    }

    private static String resolve(final String path, final String child) {
        return path + (!path.endsWith("/") ? "/" : "") + child;
    }
}
