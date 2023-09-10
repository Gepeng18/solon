package org.noear.solon.extend.schedule;

import org.noear.solon.Solon;
import org.noear.solon.core.AopContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.event.AppLoadEndEvent;

/**
 * solon.extend.schedule 相对于 cron4j-solon-plugin 的区别：
 *
 * getInterval 和 getThreads 可动态控制；例，夜间或流量小时弹性变小数值.
 *
 * */
public class XPluginImp implements Plugin {
    @Override
    public void start(AopContext context) {
        // 1、注册所有 IJob 到 JobManager 中
        context.lifecycle(-99, () -> {
            context.beanForeach((v) -> {
                if (v.raw() instanceof IJob) {
                    JobManager.register(new JobEntity(v.name(), v.raw()));
                }
            });
        });

        context.lifecycle(Integer.MAX_VALUE, () -> {
            // 2、启动 JobManager
            JobManager.run(JobRunner.global);
        });
    }
}