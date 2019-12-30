package org.freework.zk.web.ui.util;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class OrderedProperties extends Properties {
    private final Set<Object> keys = new TreeSet<Object>();

    public OrderedProperties() {
    }

    public OrderedProperties(Properties defaults) {
        super(defaults);
    }

    @Override
    public synchronized Enumeration<Object> keys() {
        return Collections.enumeration(keys);
    }

    @Override
    public Set<Object> keySet() {
        return super.keySet();
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        keys.add(key);
        return super.put(key, value);
    }

    @Override
    public synchronized void putAll(Map<?, ?> t) {
        keys.addAll(t.keySet());
        super.putAll(t);
    }

    @Override
    public synchronized Object get(Object key) {
        return super.get(key);
    }

    @Override
    public synchronized Object remove(Object key) {
        keys.remove(key);
        return super.remove(key);
    }

    @Override
    public synchronized void clear() {
        keys.clear();
        super.clear();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        Set<Map.Entry<Object, Object>> entrySet = new LinkedHashSet<Map.Entry<Object, Object>>(keys.size());
        for (Object key : keys) {
            entrySet.add(new Entry(key, get(key)));
        }

        return entrySet;
    }

    static class Entry implements Map.Entry<Object, Object> {
        private final Object key;
        private final Object value;

        private Entry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public Object setValue(Object o) {
            throw new UnsupportedOperationException("not implemented");
        }
    }
}