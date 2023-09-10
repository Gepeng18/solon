package org.noear.solon.cloud.impl;

import org.noear.solon.Solon;
import org.noear.solon.Utils;
import org.noear.solon.cloud.CloudClient;
import org.noear.solon.cloud.CloudConfigHandler;
import org.noear.solon.cloud.CloudManager;
import org.noear.solon.cloud.annotation.CloudConfig;
import org.noear.solon.cloud.model.Config;
import org.noear.solon.core.BeanBuilder;
import org.noear.solon.core.BeanWrap;

import java.util.Properties;

/**
 * @author noear
 * @since 1.4
 */
public class CloudConfigBeanBuilder implements BeanBuilder<CloudConfig> {
    public static final CloudConfigBeanBuilder instance = new CloudConfigBeanBuilder();

    @Override
    public void doBuild(Class<?> clz, BeanWrap bw, CloudConfig anno) throws Exception {
        if (CloudClient.config() == null) {
            throw new IllegalArgumentException("Missing CloudConfigService component");
        }

        CloudConfigHandler handler;
        if (bw.raw() instanceof CloudConfigHandler) {
            handler = bw.raw();
        } else {
            handler = (Config cfg) -> {
                Properties val0 = cfg.toProps();
                Utils.injectProperties(bw.raw(),val0);
            };
        }

        // 将handler放到configHandlerMap中，key为注解CloudConfig，value为handler。这里只加进去，没使用
        CloudManager.register(anno, handler);

        // cmt 启动的时候先pull，然后执行一次handler
        if (CloudClient.config() != null) {
            //支持${xxx}配置
            String name = Solon.cfg().getByParse(Utils.annoAlias(anno.value(), anno.name()));
            //支持${xxx}配置
            String group = Solon.cfg().getByParse(anno.group());

            // 拉配置，并借助configMap实现顺序更新
            Config config = CloudClient.config().pull(group, name);
            if (config != null) {
                // listener
                handler.handle(config);
            }

            // cmt attention一下，后面如果config变更，就会被通知。依赖 CloudConfigServiceWaterImp.run 的定时拉取和执行 observerMap
            //关注配置，其实就是将一个 observer 放到 observerMap 中，key是handler，value是handler&group&Key
            CloudClient.config().attention(group, name, handler);
        }
    }
}
