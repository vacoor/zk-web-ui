/*
 * Copyright (c) 2005, 2014 vacoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.freework.zk.web.ui.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.JSONPObject;
import freework.util.Throwables;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;

/**
 * 对 Jackson JSON Data Binding (De)Serialization 的简单封装
 * <p>
 * <code>
 * <pre>
 * RawType JSON --&gt; Java
 * object                     LinkedHashMap&lt;String, Object&gt;
 * array                      ArrayList&lt;Object&gt;
 * string                     String
 * number(非分数no fraction)  Integer,Long or BigInteger
 * number(fraction)           Double(configurable to use BingDecimal)
 * true|false                 Boolean
 * null                       null
 * </pre>
 * </code>
 *
 * @author changhe.yang
 */
@SuppressWarnings({"unused"})
public abstract class Jacksons {
    /* mapper 是线程安全的且可以重用 */
    private static volatile WeakReference<ObjectMapper> cache;
    private static final Object mapperMonitor = new Object();

    /**
     * 获取该类中维护的Jackson ObjectMapper对象
     *
     * @return 内部维护的 Jackson ObjectMapper
     */
    public static ObjectMapper getJacksonMapper() {
        WeakReference<ObjectMapper> ref = cache;
        ObjectMapper mapper = null;

        if (ref != null) {
            mapper = ref.get();
            if (mapper != null) {
                return mapper;
            }
        }

        synchronized (mapperMonitor) {
            ref = cache;
            if (ref == null || ref.get() == null) {
                mapper = new ObjectMapper();
                mapper = initConfig(mapper);
                cache = new WeakReference<ObjectMapper>(mapper);
            }
        }
        return mapper;
    }

    /**
     * 初始化配置
     *
     * @param mapper 要初始化的 Mapper
     * @return 初始化后的 Mapper
     */
    protected static ObjectMapper initConfig(ObjectMapper mapper) {
        // JsonParser 配置
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
                .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                .configure(JsonParser.Feature.ALLOW_COMMENTS, true);
              /*
              .configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
              .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE)
              */

        // JsonGenerator 配置
        // 转义非 ASCII 值为Unicode
        mapper.configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);

        // Serialization/Deserialization Feature可以通过disable/enable设置
        // 反序列化设置
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
              /*
              .configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true)
              .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
              */

        // 序列化设置
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
              /*
              .configure(SerializationFeature.WRAP_ROOT_VALUE, true)
              .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
              .configure(SerializationFeature.WRITE_ENUMS_USING_INDEX, true)
              */
                .configure(SerializationFeature.INDENT_OUTPUT, true);

        /*
        mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)
        // 序列化包含设置, 请使用 @JsonInclude 代替
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // 非空白 [].lenth > 0 collection/map.size > 0 , String ! "" , Object ! null
          //.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)

        /*
        // 自动检测设置, 请使用 @JsonAutoDetect 代替
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.DEFAULT)
              .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.DEFAULT)
              .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.DEFAULT);
        mapper.setVisibilityChecker(mapper.getVisibilityChecker().with(JsonAutoDetect.Visibility.NONE));
        */

        /*
        // 日期格式设置, 请使用 @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT+8"), timezone 需要指定
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        */

        //支持使用jaxb注解, 减少javaben和jackson耦合,先找jaxb annotation,如果没找到则找jackson
        //mapper.registerModule(new JaxbAnnotationModule());

        // 命名策略, 为下划线
        // mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        return mapper;
    }

    /**
     * Java Object --&gt; JSON Object
     *
     * @param object 要序列化的对象
     * @return json 字符串
     */
    public static String serialize(Object object) {
        try {
            return getJacksonMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            return Throwables.unchecked(e);
        }
    }

    public static void serialize(OutputStream outputStream, Object object) {
        try {
            getJacksonMapper().writeValue(outputStream, object);
            outputStream.flush();
        } catch (IOException e) {
            Throwables.unchecked(e);
        }
    }

    /**
     * Java Object --&gt; JSONP
     *
     * @param func   function 名称
     * @param object 要序列化的对象
     * @return JSONP 调用字符串
     */
    public static String serializeToJSONP(String func, Object object) {
        return serialize(new JSONPObject(func, object));
    }

    /**
     * JSON Object --&gt; Java Object (RawType)
     * 对于数组可使用 String[].class
     * 如果需要转换为 GenericType, 需使用以下方法
     * <p>
     * {@link #deserialize(String, com.fasterxml.jackson.core.type.TypeReference)}
     * {@link #deserialize(String, com.fasterxml.jackson.databind.JavaType)}
     *
     * @param json json 字符串
     * @param type 对象类型
     * @param <T>  实例类型
     * @return 反序列化后的对象
     */
    public static <T> T deserialize(String json, Class<T> type) {
        return deserialize(json, constructType(type));
    }

    /**
     * JSON Object --&gt; Java Object (support genericType)
     * <p>
     * 对于泛型类型 可以使用 类似new TypeReference&lt;Map&lt;String, String&gt;&gt;
     *
     * @param json
     * @param typeRef
     * @param <T>
     * @return
     */
    public static <T> T deserialize(String json, TypeReference<T> typeRef) {
        return deserialize(json, constructType(typeRef));
    }

    /**
     * JSON Object --&gt; Java Object (support genericType)
     * 可以通过 {@link #constructGenericType(Class, Class[])} 构建JavaType实例
     *
     * @param json
     * @param javaType
     * @param <T>
     * @return
     */
    public static <T> T deserialize(String json, JavaType javaType) {
        try {
            return getJacksonMapper().readValue(json, javaType);
        } catch (IOException e) {
            return Throwables.unchecked(e);
        }
    }

    public static <T> T deserialize(InputStream inputStream, Class<T> type) {
        try {
            return getJacksonMapper().readValue(inputStream, type);
        } catch (IOException e) {
            return Throwables.unchecked(e);
        }
    }

    public static <T> T deserialize(InputStream inputStream, TypeReference<T> typeRef) {
        try {
            return getJacksonMapper().readValue(inputStream, typeRef);
        } catch (IOException e) {
            return Throwables.unchecked(e);
        }
    }

    /**
     * 使用 json 中的属性来更新 给定的bean属性
     *
     * @param json
     * @param bean
     * @param <T>
     * @return
     */
    public static <T> T deserializeForUpdating(String json, T bean) {
        try {
            return getJacksonMapper().readerForUpdating(bean).<T>readValue(json);
        } catch (IOException e) {
            return Throwables.unchecked(e);
        }
    }

    public static <T> T deserializeForUpdating(InputStream inputStream, T bean) {
        try {
            return getJacksonMapper().readerForUpdating(bean).<T>readValue(inputStream);
        } catch (IOException e) {
            return Throwables.unchecked(e);
        }
    }

    // -----------------------------

    /**
     * 获取 ObjectWriter, 以便该ObjectWriter的序列化做进一步设置
     * 该方法始终返回一个新的基于Mapper配置的ObjectWriter
     *
     * @return
     */
    public static ObjectWriter writer() {
        return getJacksonMapper().writer();
    }

    /**
     * 获取 ObjectReader, 以便做进一步的反序列化设置
     * 该方法始终返回一个新的基于Mapper配置的ObjectReader
     *
     * @return
     */
    public static ObjectReader reader(Class<?> type) {
        return reader(constructType(type));
    }

    public static ObjectReader reader(TypeReference<?> typeRef) {
        return reader(constructType(typeRef));
    }

    public static ObjectReader reader(JavaType javaType) {
        return getJacksonMapper().reader(javaType);
    }

    // ---------------------------------------------

    public static JavaType constructType(Type type) {
        return getJacksonMapper().getTypeFactory().constructType(type);
    }

    public static JavaType constructType(TypeReference<?> typeRef) {
        return getJacksonMapper().getTypeFactory().constructType(typeRef);
    }

    /**
     * 根据给定的 RawType 和类型参数构建相应的javaType
     *
     * @param rawType
     * @param paramType
     * @return
     */
    public static JavaType constructGenericType(Class<?> rawType, Class<?>... paramType) {
        return getJacksonMapper().getTypeFactory().constructParametricType(rawType, paramType);
    }

    // ---------------
    public static JsonNode readTree(String json) {
        // JsonNode root = NullNode.getInstance();
        try {
            return getJacksonMapper().readTree(json);
        } catch (IOException e) {
            return Throwables.unchecked(e);
        }
    }

    // --------------
    public static ObjectNode createObjectNode() {
        return getJacksonMapper().createObjectNode();
    }

    public static ArrayNode createArrayNode(Object... values) {
        ArrayNode arrayNode = getJacksonMapper().createArrayNode();
        for (Object value : values) {
            arrayNode.addPOJO(value);
        }
        return arrayNode;
    }

    private Jacksons() {
    }
}
