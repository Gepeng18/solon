package org.noear.solon.data.cache;

import org.noear.solon.Utils;
import org.noear.solon.core.BeanWrap;

import java.util.function.Consumer;

/**
 * 缓存服务事件监控器。监听BeanWrap，获得CacheService bean
 * 就干了一件事，将 CacheService 这个bean塞到一个全局map中，这样好让其他类能够直接通过static静态方法拿到
 *
 * @author noear
 * @since 1.0
 * */
public class CacheServiceWrapConsumer implements Consumer<BeanWrap> {
    @Override
    public void accept(BeanWrap bw) {
        if (Utils.isEmpty(bw.name())) {
            CacheLib.cacheServiceAdd("", bw.raw());
        } else {
            CacheLib.cacheServiceAddIfAbsent(bw.name(), bw.raw());

            if (bw.typed()) {
                CacheLib.cacheServiceAdd("", bw.raw());
            }
        }
    }
}
