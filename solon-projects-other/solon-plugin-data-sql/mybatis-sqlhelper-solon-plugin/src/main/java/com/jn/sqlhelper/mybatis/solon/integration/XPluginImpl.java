package com.jn.sqlhelper.mybatis.solon.integration;

import org.noear.solon.core.*;

public class XPluginImpl implements Plugin {
    @Override
    public void start(AopContext context) {
        context.beanMake(SqlHelperConfiguration.class);
    }
}
