package org.noear.solon.cloud.extend.water.service;

import org.noear.solon.Solon;
import org.noear.solon.Utils;
import org.noear.solon.cloud.CloudConfigHandler;
import org.noear.solon.cloud.CloudProps;
import org.noear.solon.cloud.model.Config;
import org.noear.solon.cloud.service.CloudConfigObserverEntity;
import org.noear.solon.cloud.service.CloudConfigService;
import org.noear.solon.cloud.utils.IntervalUtils;
import org.noear.solon.core.event.EventBus;
import org.noear.water.WaterClient;
import org.noear.water.model.ConfigM;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * 配置服务
 *
 * @author noear
 * @since 1.2
 */
public class CloudConfigServiceWaterImp extends TimerTask implements CloudConfigService {
    private final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private long refreshInterval;

    private Map<String, Config> configMap = new HashMap<>();


    public CloudConfigServiceWaterImp(CloudProps cloudProps) {
        refreshInterval = IntervalUtils.getInterval(cloudProps.getConfigRefreshInterval("5s"));
    }

    /**
     * 配置刷新间隔时间（仅当isFilesMode时有效）
     */
    public long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * 定时执行，刷新所有的配置（无视本地缓存），然后调用observerMap中的handler的handle方法
     */
    @Override
    public void run() {
        try {
            run0();
        } catch (Throwable e) {
            EventBus.publishTry(e);
        }
    }

    private void run0() {
        if (Solon.cfg().isFilesMode()) {
            Set<String> loadGroups = new LinkedHashSet<>();

            try {
                observerMap.forEach((k, v) -> {
                    if (loadGroups.contains(v.group) == false) {
                        loadGroups.add(v.group);
                        // 刷新配置，无视本地缓存
                        WaterClient.Config.reload(v.group);
                    }

                    // 本地缓存中有，就直接返回，没有则调用water server接口获取tag标签对应的所有配置，放到本地缓存中
                    ConfigM cfg = WaterClient.Config.get(v.group, v.key);

                    onUpdateDo(v.group, v.key, cfg, cfg2 -> {
                        v.handle(cfg2);
                    });
                });
            } catch (Throwable ex) {

            }
        }
    }

    /**
     * 拉取配置，并借助configMap实现顺序更新
     */
    @Override
    public Config pull(String group, String key) {
        if (Utils.isEmpty(group)) {
            group = Solon.cfg().appGroup();

            if (Utils.isEmpty(group)) {
                group = DEFAULT_GROUP;
            }
        }

        // 感觉像顺序更新？
        // 拉取配置（本地缓存中有，就直接返回，没有则调用water server接口获取tag标签对应的所有配置，放到本地缓存中）
        ConfigM cfg = WaterClient.Config.get(group, key);

        String cfgKey = group + "/" + key;
        // configMap的作用是顺序更新
        Config config = configMap.get(cfgKey);

        if (config == null) {
            config = new Config(group, key, cfg.value, cfg.lastModified);
            configMap.put(cfgKey, config);
        } else if (cfg.lastModified > config.version()) {
            config.updateValue(cfg.value, cfg.lastModified);
        }

        return config;
    }

    /**
     * 调用api进行更新
     */
    @Override
    public boolean push(String group, String key, String value) {
        if (Utils.isEmpty(group)) {
            group = Solon.cfg().appGroup();

            if (Utils.isEmpty(group)) {
                group = DEFAULT_GROUP;
            }
        }

        try {
            WaterClient.Config.set(group, key, value);
            return true;
        } catch (IOException e) {
            EventBus.publishTry(e);
            return false;
        }
    }

    @Override
    public boolean remove(String group, String key) {
        return false;
    }

    private Map<CloudConfigHandler, CloudConfigObserverEntity> observerMap = new HashMap<>();

    /**
     * observer 关注 group 下的某个key
     * 其实就是将一个 observer 放到 observerMap 中，key是handler，value是handler&group&Key
     */
    @Override
    public void attention(String group, String key, CloudConfigHandler observer) {
        if (observerMap.containsKey(observer)) {
            return;
        }

        if (Utils.isEmpty(group)) {
            group = Solon.cfg().appGroup();

            if (Utils.isEmpty(group)) {
                group = DEFAULT_GROUP;
            }
        }

        CloudConfigObserverEntity entity = new CloudConfigObserverEntity(group, key, observer);
        observerMap.put(observer, entity);
    }

    public void onUpdate(String group, String key) {
        if (Utils.isEmpty(group)) {
            return;
        }

        // 加载某个组
        WaterClient.Config.reload(group);
        // 获取组下的某个key对应的配置
        ConfigM cfg = WaterClient.Config.get(group, key);

        onUpdateDo(group, key, cfg, (cfg2) -> {
            observerMap.forEach((k, v) -> {
                if (group.equals(v.group) && key.equals(v.key)) {
                    v.handle(cfg2);
                }
            });
        });
    }

    /**
     * 该类为了保证顺序更新，顺序消费consumer
     */
    private void onUpdateDo(String group, String key, ConfigM cfg, Consumer<Config> consumer) {
        String cfgKey = group + "/" + key;
        Config config = configMap.get(cfgKey);

        if (config == null) {
            config = new Config(group, key, cfg.value, cfg.lastModified);
        } else {
            if (config.version() < cfg.lastModified) {
                config.updateValue(cfg.value, cfg.lastModified);
            } else {
                return;
            }
        }

        consumer.accept(config);
    }
}
