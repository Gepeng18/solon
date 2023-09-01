package org.noear.solon.data.cache;

import java.util.Properties;
import java.util.function.Supplier;

/**
 * CacheService 供应者，根据注册的工厂获取对应的缓存服务
 *
 * @author noear
 * @since 1.5
 */
public class CacheServiceSupplier implements Supplier<CacheService> {
    private CacheService real;
    private String driverType;

    /**
     * 1、从配置中获取 driverType，这代表缓存的类型
     * 2、调用工厂的工厂根据driverType获取缓存工厂，然后调用缓存工厂来创建一个工厂
     */
    public CacheServiceSupplier(Properties props) {
        driverType = props.getProperty("driverType");

        // 各个插件：XPluginImp 负责将 缓存工厂设置到这里的工厂的工厂中，
        // 所以这段代码就是直接通过driverType从工厂的工厂取出来即可
        CacheFactory factory = CacheLib.cacheFactoryGet(driverType);

        if (factory != null) {
            real = factory.create(props);
        } else {
            throw new IllegalArgumentException("There is no supported driverType");
        }
    }

    @Override
    public CacheService get() {
        return real;
    }
}
