package org.noear.solon.data.integration;

import org.noear.solon.Solon;
import org.noear.solon.core.*;
import org.noear.solon.data.annotation.*;
import org.noear.solon.data.cache.*;
import org.noear.solon.data.tran.TranExecutor;
import org.noear.solon.data.around.CacheInterceptor;
import org.noear.solon.data.around.CachePutInterceptor;
import org.noear.solon.data.around.CacheRemoveInterceptor;
import org.noear.solon.data.around.TranInterceptor;
import org.noear.solon.data.tran.TranExecutorImp;

public class XPluginImp implements Plugin {
    @Override
    public void start(AopContext context) {
        //注册缓存工厂
        CacheLib.cacheFactoryAdd("local", new LocalCacheFactoryImpl());

        //添加事务控制支持
        if (Solon.app().enableTransaction()) {
            context.wrapAndPut(TranExecutor.class, TranExecutorImp.global);

            context.beanInterceptorAdd(Tran.class, new TranInterceptor(), 120);
        }

        //添加缓存控制支持
        if (Solon.app().enableCaching()) {
            // 向全局map中增加一个本地缓存
            CacheLib.cacheServiceAddIfAbsent("", LocalCacheService.instance);

            // 将 CacheService 这个bean塞到一个全局map中，这样好让其他类能够直接通过static静态方法拿到
            // 我个人感觉这一行代码和下一行代码应该调换一下顺序更容易理解，整体的逻辑应该是这样
            // 1、solon扫描到了CacheService，放到了solon容器中
            // 2、如果solon容器中没有，那就塞进去一个本地缓存
            // 3、将solon容器中的CacheService放到全局map中，这样可以通过static静态方法拿到
            context.subWrapsOfType(CacheService.class, new CacheServiceWrapConsumer());

            context.lifecycle(-99, () -> {
                // 如果solon容器中没有缓存，那么就增加一个本地缓存放到solon的容器中
                if (context.hasWrap(CacheService.class) == false) {
                    context.wrapAndPut(CacheService.class, LocalCacheService.instance);
                }
            });

            // 注解生效是通过方法的拦截器
            context.beanInterceptorAdd(CachePut.class, new CachePutInterceptor(), 110);
            context.beanInterceptorAdd(CacheRemove.class, new CacheRemoveInterceptor(), 110);
            context.beanInterceptorAdd(Cache.class, new CacheInterceptor(), 111);
        }
    }
}
