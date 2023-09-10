package org.noear.solon.cloud.extend.water;

import org.noear.solon.Solon;
import org.noear.solon.Utils;
import org.noear.solon.cloud.CloudClient;
import org.noear.solon.cloud.CloudManager;
import org.noear.solon.cloud.CloudProps;
import org.noear.solon.cloud.annotation.EventLevel;
import org.noear.solon.cloud.extend.water.integration.http.*;
import org.noear.solon.cloud.extend.water.integration.msg.HandlerCacheUpdate;
import org.noear.solon.cloud.extend.water.integration.msg.HandlerConfigUpdate;
import org.noear.solon.cloud.extend.water.service.*;
import org.noear.solon.cloud.model.Config;
import org.noear.solon.cloud.model.Instance;
import org.noear.solon.core.AopContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.bean.InitializingBean;
import org.noear.water.WW;
import org.noear.water.WaterAddress;
import org.noear.water.WaterClient;
import org.noear.water.WaterSetting;

import java.util.Timer;

/**
 * @author noear
 * @since 1.2
 */
public class XPluginImp implements Plugin, InitializingBean {
    private Timer clientTimer = new Timer();
    private CloudProps cloudProps;
    private boolean inited = false;

    private boolean initDo(AopContext context) throws Throwable {
        if (cloudProps == null) {
            cloudProps = new CloudProps(context, "water");
        }

        if (inited) {
            return true;
        }

        if (Utils.isEmpty(cloudProps.getServer())) {
            return false;
        }

        //1.初始化服务地址
        String server = cloudProps.getServer();
        String configServer = cloudProps.getConfigServer();
        String discoveryServer = cloudProps.getDiscoveryServer();
        String eventServer = cloudProps.getEventServer();
        String logServer = cloudProps.getLogServer();

        String logDefault = cloudProps.getLogDefault();

        CloudProps.LOG_DEFAULT_LOGGER = logDefault;

        //1.1.设置water默认基础配置
        System.setProperty(WW.water_host, server);
        if (Utils.isNotEmpty(logDefault)) {
            System.setProperty(WW.water_logger, logDefault);
        }

        if (server.equals(configServer) == false) {
            WaterAddress.setCfgApiUrl(configServer);
        }

        if (server.equals(discoveryServer) == false) {
            WaterAddress.setRegApiUrl(discoveryServer);
        }

        if (server.equals(eventServer) == false) {
            WaterAddress.setMsgApiUrl(eventServer);
        }

        if (server.equals(logServer) == false) {
            WaterAddress.setLogApiUrl(logServer);
        }

        inited = true;
        return true;
    }

    @Override
    public void afterInjection() throws Throwable {
        if (initDo(Solon.context()) == false) {
            return;
        }

        CloudTraceServiceWaterImp traceServiceImp = new CloudTraceServiceWaterImp();
        WaterClient.localHostSet(Instance.local().address());
        WaterClient.localServiceSet(Instance.local().service());
        WaterSetting.water_trace_id_supplier(traceServiceImp::getTraceId);

        if (cloudProps.getLogEnable()) {
            CloudManager.register(new CloudLogServiceWaterImp(cloudProps));
        }

        if (cloudProps.getTraceEnable()) {
            CloudManager.register(traceServiceImp);
        }
    }

    @Override
    public void start(AopContext context) throws Throwable {
        if (initDo(context) == false) {
            return;
        }

        //2.初始化服务
        CloudDiscoveryServiceWaterImp discoveryServiceImp = null;
        CloudConfigServiceWaterImp configServiceImp = null;
        CloudEventServiceWaterImp eventServiceImp = new CloudEventServiceWaterImp(cloudProps);
        CloudI18nServiceWaterImp i18nServiceImp = null;

        if (cloudProps.getMetricEnable()) {
            CloudManager.register(new CloudMetricServiceWaterImp());
        }


        // 开启配置
        if (cloudProps.getConfigEnable()) {
            configServiceImp = new CloudConfigServiceWaterImp(cloudProps);
            CloudManager.register(configServiceImp);

            // 定时更新 observerMap 中的 observer
            if (Solon.cfg().isFilesMode()) {
                if (configServiceImp.getRefreshInterval() > 0) {
                    long interval = configServiceImp.getRefreshInterval();
                    // 定时执行，刷新所有的配置（无视本地缓存），然后调用observerMap中的handler的handle方法
                    clientTimer.schedule(configServiceImp, interval, interval);
                }
            }

            // water配置加载，就是首次加载配置+将observer放到 observerMap 来定期加载到全局配置properties中
            CloudClient.configLoad(cloudProps.getConfigLoad());
        }

        if (cloudProps.getI18nEnable()) {
            i18nServiceImp = new CloudI18nServiceWaterImp(cloudProps);
            CloudManager.register(i18nServiceImp);
        }


        // 开启服务发现
        if (cloudProps.getDiscoveryEnable()) {
            discoveryServiceImp = new CloudDiscoveryServiceWaterImp(cloudProps);
            CloudManager.register(discoveryServiceImp);

            if (Solon.cfg().isFilesMode()) {
                if (discoveryServiceImp.getRefreshInterval() > 0) {
                    long interval = discoveryServiceImp.getRefreshInterval();
                    clientTimer.schedule(discoveryServiceImp, interval, interval);
                }
            }
        }

        // 开启事件总线
        if (cloudProps.getEventEnable()) {
            String receive = getEventReceive();
            if (receive != null && receive.startsWith("@")) {
                if (CloudClient.config() != null) {
                    Config cfg = CloudClient.config().pull(Solon.cfg().appGroup(), receive.substring(1));
                    if (cfg == null || Utils.isEmpty(cfg.value())) {
                        throw new IllegalArgumentException("Configuration " + receive + " does not exist");
                    }
                    setEventReceive(cfg.value());
                }
            }

            CloudManager.register(eventServiceImp);

            // water自己的的更新
            if (discoveryServiceImp != null || i18nServiceImp != null) {
                //关注缓存更新事件
                eventServiceImp.attention(EventLevel.instance, "", "", WW.msg_ucache_topic, "",
                        0, new HandlerCacheUpdate(discoveryServiceImp, i18nServiceImp));
            }

            // water自己的的更新
            if (configServiceImp != null) {
                //关注配置更新事件
                eventServiceImp.attention(EventLevel.instance, "", "", WW.msg_uconfig_topic, "",
                       0, new HandlerConfigUpdate(configServiceImp));
            }

            context.lifecycle(-99, () -> eventServiceImp.subscribe());
        }

        if (cloudProps.getLockEnable()) {
            CloudManager.register(new CloudLockServiceWaterImp());
        }

        if (cloudProps.getListEnable()) {
            CloudManager.register(new CloudListServiceWaterImp());
        }

        if (cloudProps.getJobEnable()) {
            CloudManager.register(CloudJobServiceWaterImp.instance);

            context.lifecycle(-99, () -> {
                CloudJobServiceWaterImp.instance.push();
            });
        }


        //3.注册http监听
        if (cloudProps.getJobEnable()) {
            // Server调用本机的job执行
            Solon.app().http(WW.path_run_job, new HandlerJob());
        }

        Solon.app().http(WW.path_run_check, new HandlerCheck());
        Solon.app().http(WW.path_run_status, new HandlerStatus());
        Solon.app().http(WW.path_run_stop, new HandlerStop());
        // Server发送msg给本机
        Solon.app().http(WW.path_run_msg, new HandlerReceive(eventServiceImp));
    }

    @Override
    public void prestop() throws Throwable {
        if (clientTimer != null) {
            clientTimer.cancel();
        }
    }

    public String getEventReceive() {
        return cloudProps.getValue(WaterProps.PROP_EVENT_receive);
    }

    public void setEventReceive(String value) {
        cloudProps.setValue(WaterProps.PROP_EVENT_receive, value);
    }
}