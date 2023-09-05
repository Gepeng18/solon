package org.noear.solon.data.cache;

import org.noear.solon.Utils;
import org.noear.solon.core.aspect.Invocation;
import org.noear.solon.data.annotation.Cache;
import org.noear.solon.data.annotation.CachePut;
import org.noear.solon.data.annotation.CacheRemove;
import org.noear.solon.data.util.InvKeys;
import org.noear.solon.core.util.SupplierEx;

/**
 * 缓存执行器
 *
 * @author noear
 * @since 1.0
 * */
public class CacheExecutorImp {
    public static final CacheExecutorImp global = new CacheExecutorImp();

    /**
     * 添加缓存
     * 1、构建缓存key（如果有注解的key，优先用）
     * 2、从缓存获取，缓存中则直接返回
     * 3、缓存中没有，则调用方法，当获取的结果不为null，则进行缓存
     * 4、向缓存中添加标签，其实就是调用cacheService的add方法向key为tag，value为List<key>中的value再添加一个key。
     *
     * @param anno     注解
     * @param inv      拦截动作
     * @param executor 真实执行者
     */
    public Object cache(Cache anno, Invocation inv, SupplierEx executor) throws Throwable {
        if (anno == null) {
            return executor.get();
        }

        //0.构建缓存key（如果有注解的key，优先用）
        String key = anno.key();
        if (Utils.isEmpty(key)) {
            //没有注解key，生成一个key
            key = InvKeys.buildByInv(inv);
        } else {
            //格式化key
            key = InvKeys.buildByTmlAndInv(key, inv);
        }


        Object result = null;
        CacheService cs = CacheLib.cacheServiceGet(anno.service());

        String keyLock = key + ":lock";

        synchronized (keyLock.intern()) {

            //1.从缓存获取
            //
            result = cs.get(key);

            if (result == null) {
                //2. 缓存中没有，执行调用，并返回
                //
                result = executor.get();

                if (result != null) {
                    //3. 调用方法获取的结果不为null，则进行缓存
                    //
                    cs.store(key, result, anno.seconds());

                    if (Utils.isNotEmpty(anno.tags())) {
                        String tags = InvKeys.buildByTmlAndInv(anno.tags(), inv, result);
                        CacheTags ct = new CacheTags(cs);

                        //4.添加缓存标签
                        for (String tag : tags.split(",")) {
                            ct.add(tag, key, anno.seconds());
                        }
                    }
                }
            }

            return result;
        }
    }

    /**
     * 清除缓存
     * 根据service获取CacheService
     * 1、调用相关服务的remove清除缓存
     * 2、移除标签相关的所有缓存
     *    2.1、根据tag遍历所有的key，每个key如果不以特殊标志开头，那么就从缓存中删除这个key
     *    2.2、再从缓存中将tag对应的value全部删除
     *
     * @param anno     注解
     * @param inv      拦截动作
     * @param rstValue 结果值
     */
    public void cacheRemove(CacheRemove anno, Invocation inv, Object rstValue) {
        if (anno == null) {
            return;
        }

        // 根据service获取缓存服务
        CacheService cs = CacheLib.cacheServiceGet(anno.service());

        //按 key 清除缓存
        if (Utils.isNotEmpty(anno.keys())) {
            String keys = InvKeys.buildByTmlAndInv(anno.keys(), inv, rstValue);

            // 调用缓存服务删除这些key
            for (String key : keys.split(",")) {
                cs.remove(key);
            }
        }

        //按 tags 清除缓存
        if (Utils.isNotEmpty(anno.tags())) {
            String tags = InvKeys.buildByTmlAndInv(anno.tags(), inv, rstValue);
            CacheTags ct = new CacheTags(cs);

            for (String tag : tags.split(",")) {
                ct.remove(tag);
            }
        }
    }

    /**
     * 缓存更新
     * 根据service获取CacheService
     * 1、调用相关服务的store更新缓存
     * 2、更新标签相关的所有缓存
     *    根据tag遍历所有的key，每个key如果不以特殊标志开头，那么就判断缓存中是否有这个key，如果没这个key那就什么也不做，
     *    如果有这个key，那就判断本次要刷新的value是否为null，为null就删除这个key，不为null，就把value写到这个key中。
     * @param anno     注解
     * @param inv      拦截动作
     * @param rstValue 结果值（将做更新值用）
     */
    public void cachePut(CachePut anno, Invocation inv, Object rstValue) {
        if (anno == null) {
            return;
        }

        // 获取缓存服务
        CacheService cs = CacheLib.cacheServiceGet(anno.service());

        //按 key 更新缓存
        if (Utils.isNotEmpty(anno.key())) {
            String key = InvKeys.buildByTmlAndInv(anno.key(), inv, rstValue);
            cs.store(key, rstValue, anno.seconds());
        }

        //按 tags 更新缓存
        if (Utils.isNotEmpty(anno.tags())) {
            String tags = InvKeys.buildByTmlAndInv(anno.tags(), inv, rstValue);
            CacheTags ct = new CacheTags(cs);

            for (String tag : tags.split(",")) {
                ct.update(tag, rstValue, anno.seconds());
            }
        }
    }
}