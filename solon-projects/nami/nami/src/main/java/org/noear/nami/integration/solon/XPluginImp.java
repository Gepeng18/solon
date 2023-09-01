package org.noear.nami.integration.solon;

import org.noear.nami.*;
import org.noear.nami.annotation.NamiClient;
import org.noear.nami.common.InfoUtils;
import org.noear.solon.Utils;
import org.noear.solon.core.AopContext;
import org.noear.solon.core.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author noear
 * @since 1.2
 * */
public class XPluginImp implements Plugin {
    private Map<NamiClient, Object> cached = new LinkedHashMap<>();

    /**
     * eg. @NamiClient(url = "http://localhost:9001/rpc/v1/user", headers = "Content-Type=application/json")
     * 代码逻辑非常简单，就是在注入的时候，对其进行动态代理，然后再注入即可
     * 动态代理逻辑见： {@link NamiHandler#invoke}，就是执行http调用
     */
    @Override
    public void start(AopContext context) {
        if (NamiConfigurationDefault.proxy == null) {
            NamiConfigurationDefault.proxy = new NamiConfigurationSolon(context);
        }

        context.beanInjectorAdd(NamiClient.class, (varH, anno) -> {
            // 加了NamiClient注解的属性必须是个接口
            if (varH.getType().isInterface() == false) {
                return;
            }

            if (Utils.isEmpty(anno.url()) && Utils.isEmpty(anno.name())) {
                NamiClient anno2 = varH.getType().getAnnotation(NamiClient.class);
                if (anno2 != null) {
                    anno = anno2;
                }
            }

            if (Utils.isEmpty(anno.url()) && Utils.isEmpty(anno.name()) && anno.upstream().length == 0) {
                throw new NamiException("@NamiClient configuration error: " + varH.getFullName());
            } else {
                InfoUtils.print(varH.getType(), anno);
            }

            Object obj = cached.get(anno);
            if (obj == null) {
                synchronized (anno) {
                    obj = cached.get(anno);
                    if (obj == null) {
                        // 创建代理，并设置到缓存中去
                        obj = Nami.builder().create(varH.getType(), anno);
                        cached.put(anno, obj);
                    }
                }
            }

            varH.setValue(obj);
        });
    }
}
