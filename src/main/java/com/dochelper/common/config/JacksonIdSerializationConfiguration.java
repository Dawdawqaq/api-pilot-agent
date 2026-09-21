package com.dochelper.common.config;

import java.util.List;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一保护雪花 ID，避免浏览器将 64 位整数解析为不精确的 JavaScript Number。
 */
@Configuration
public class JacksonIdSerializationConfiguration {

    /**
     * 仅把名称为 id 或以 Id 结尾的 long 属性序列化为字符串，普通计数和耗时仍保持数字类型。
     *
     * @return ID 序列化模块
     */
    @Bean
    public SimpleModule idSerializationModule() {
        SimpleModule module = new SimpleModule("dochelper-id-serialization");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(
                    SerializationConfig config,
                    BeanDescription beanDescription,
                    List<BeanPropertyWriter> properties
            ) {
                properties.stream()
                        .filter(JacksonIdSerializationConfiguration::isLongIdProperty)
                        .forEach(property -> property.assignSerializer(ToStringSerializer.instance));
                return properties;
            }
        });
        return module;
    }

    private static boolean isLongIdProperty(BeanPropertyWriter property) {
        String name = property.getName();
        Class<?> rawType = property.getType().getRawClass();
        boolean idName = "id".equals(name) || name.endsWith("Id");
        return idName && (Long.class.equals(rawType) || Long.TYPE.equals(rawType));
    }
}
