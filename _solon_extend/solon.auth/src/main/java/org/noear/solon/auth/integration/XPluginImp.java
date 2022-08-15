package org.noear.solon.auth.integration;

import org.noear.solon.SolonApp;
import org.noear.solon.auth.AuthAdapterSupplier;
import org.noear.solon.auth.AuthUtil;
import org.noear.solon.auth.annotation.*;
import org.noear.solon.auth.interceptor.*;
import org.noear.solon.core.Aop;
import org.noear.solon.core.AopContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.event.EventBus;

/**
 * @author noear
 * @since 1.3
 */
public class XPluginImp implements Plugin {
    @Override
    public void start(AopContext context) {
        context.beanAroundAdd(AuthIp.class, new IpInterceptor());
        context.beanAroundAdd(AuthLogined.class, new LoginedInterceptor());
        context.beanAroundAdd(AuthPath.class, new PathInterceptor());
        context.beanAroundAdd(AuthPermissions.class, new PermissionsInterceptor());
        context.beanAroundAdd(AuthRoles.class, new RolesInterceptor());

        EventBus.subscribe(AuthAdapterSupplier.class, e -> AuthUtil.adapterAdd(e));
    }
}
