package org.noear.solon.logging.integration;

import org.noear.solon.Solon;
import org.noear.solon.core.AopContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.bean.InitializingBean;
import org.noear.solon.core.util.ClassUtil;
import org.noear.solon.logging.AppenderManager;
import org.noear.solon.logging.LogOptions;
import org.noear.solon.logging.event.Appender;
import org.slf4j.MDC;

import java.util.Properties;


/**
 * @author noear
 * @since 1.3
 */
public class XPluginImp implements Plugin , InitializingBean {
    @Override
    public void afterInjection() throws Throwable {
        AppenderManager.init();
    }

    /**
     * 1、根据配置指明使用什么 Appender，然后将其放到 appenderMap 中
     * 2、初始化记录器默认等级，读取 solon.logging.logger 对应的属性，key为 root.level，value为 DEBUG，则默认的rootLevel就是 DEBUG
     * 3、在 Solon.app()中多增加一个filter，在filter中清除MDC的数据
     */

    /**
     * solon.logging.appender:
     *   console:
     *     level: DEBUG
     *   file:
     *     level: INFO
     *     rolling: "logs/demoapp_%d{yyyy-MM-dd}/%i${FILE_LOG_EXTENSION}"
     *     maxFileSize: "1 KB"
     *   cloud:
     *     level: INFO
     *   json:
     *     level: DEBUG
     *     class: "features.AppenderImpl"
     */
    @Override
    public void start(AopContext context) {
        Properties props = Solon.cfg().getProp("solon.logging.appender");

        //初始化
        AppenderManager.init();

        //注册添加器
        if (props.size() > 0) {
            props.forEach((k, v) -> {
                String key = (String) k;
                String val = (String) v;

                if (key.endsWith(".class")) {
                    Appender appender = ClassUtil.tryInstance(val);
                    if (appender != null) {
                        // eg. key: json，value：features.AppenderImpl
                        String name = key.substring(0, key.length() - 6);
                        // 注册到 appenderMap中
                        AppenderManager.register(name, appender);
                    }
                }
            });
        }

        //init 初始化记录器等级配置
        LogOptions.getLoggerLevelInit();

        // 在 Solon.app()中多增加一个filter，在filter中清除MDC的数据
        Solon.app().filter(-9, (ctx, chain) -> {
            MDC.clear();
            chain.doFilter(ctx);
        });
    }

    @Override
    public void stop() throws Throwable {
        AppenderManager.stop();
    }
}
