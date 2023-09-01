package org.noear.solon.data.cache;

/**
 * 二级缓存服务
 *
 * @author noear
 * @since 1.2
 * */
public class SecondCacheService implements CacheService {
    private CacheService cache1;
    private CacheService cache2;
    private int bufferSeconds;


    /**
     * @param cache1 一级缓存
     * @param cache2 二级缓存
     * */
    public SecondCacheService(CacheService cache1, CacheService cache2) {
        this(cache1, cache2, 5);
    }

    /**
     * @param cache1 一级缓存
     * @param cache2 二级缓存
     * @param bufferSeconds 缓冲秒数
     * */
    public SecondCacheService(CacheService cache1, CacheService cache2, int bufferSeconds) {
        this.cache1 = cache1;
        this.cache2 = cache2;
        this.bufferSeconds = bufferSeconds;
    }

    /**
     * 多级缓存的写入，就是将数据分别调用一级工厂和二级工厂的写入操作
     */
    @Override
    public void store(String key, Object obj, int seconds) {
        cache1.store(key, obj, seconds);
        cache2.store(key, obj, seconds);
    }

    /**
     * 多级缓存的查询，就是先判断一级缓存中是否有，有就返回。没有，就调用二级缓存去查询，如果查到了就写到一级缓存中。
     */
    @Override
    public Object get(String key) {
        Object temp = cache1.get(key);
        if (temp == null) {
            temp = cache2.get(key);
            if (bufferSeconds > 0 && temp != null) {
                cache1.store(key, temp, bufferSeconds);
            }
        }
        return temp;
    }

    @Override
    public void remove(String key) {
        cache2.remove(key);
        cache1.remove(key);
    }
}
