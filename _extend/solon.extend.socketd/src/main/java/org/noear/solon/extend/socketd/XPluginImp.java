package org.noear.solon.extend.socketd;

import org.noear.solon.SolonApp;
import org.noear.solon.Utils;
import org.noear.solon.core.Aop;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.message.Listener;
import org.noear.solon.core.message.Session;
import org.noear.solon.extend.socketd.annotation.ClientEndpoint;
import org.noear.solon.extend.socketd.util.MessageUtil;

public class XPluginImp implements Plugin {
    @Override
    public void start(SolonApp app) {
        //注册 @ClientListenEndpoint 构建器
        Aop.context().beanBuilderAdd(ClientEndpoint.class, (clz, wrap, anno) -> {
            if (Listener.class.isAssignableFrom(clz)) {
                Listener l = wrap.raw();

                //创建会话
                Session s = SocketD.createSession(anno.uri());

                //绑定监听
                s.listener(l);

                //发送握手包
                if (Utils.isNotEmpty(anno.handshakeHeader())) {
                    s.sendHandshake(MessageUtil.wrapHandshake(anno.handshakeHeader()));
                }

                //设定自动心跳
                if (anno.heartbeatRate() > 0) {
                    s.sendHeartbeatAuto(anno.heartbeatRate());
                }
            }
        });
    }
}
