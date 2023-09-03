package org.noear.solon.cloud.impl;

import org.noear.solon.Utils;
import org.noear.solon.cloud.service.CloudTraceService;
import org.noear.solon.core.handle.Context;

/**
 * @author noear
 * @since 1.3
 */
public class CloudTraceServiceImpl implements CloudTraceService {
    @Override
    public String HEADER_TRACE_ID_NAME() {
        return "X-Solon-Trace-Id";
    }

    @Override
    public String HEADER_FROM_ID_NAME() {
        return "X-Solon-From-Id";
    }

    static final ThreadLocal<String> traceIdLocal = new InheritableThreadLocal<>();

    @Override
    public void setLocalTraceId(String traceId) {
        traceIdLocal.set(traceId);
    }

    /**
     * 从threadLocal或者context中获取uuid，如果没有就生成，然后放进去
     */
    @Override
    public String getTraceId() {
        Context ctx = Context.current();

        // context如果为null，则生成一个uuid，放到threadLocal中
        if (ctx == null) {
            String traceId = traceIdLocal.get();
            if (Utils.isEmpty(traceId)) {
                traceId = Utils.guid();
                traceIdLocal.set(traceId);
            }

            return traceId;
        } else {
            // context如果不为null，则生成一个uuid，放到header中
            String traceId = ctx.header(HEADER_TRACE_ID_NAME());

            if (Utils.isEmpty(traceId)) {
                traceId = Utils.guid();
                ctx.headerMap().put(HEADER_TRACE_ID_NAME(), traceId);
            }

            return traceId;
        }
    }

    /**
     * 从header中取出fromId，如果fromId为空，则从context中获取readIp，设置到header中
     */
    @Override
    public String getFromId() {
        Context ctx = Context.current();

        if (ctx == null) {
            return "";
        } else {
            String fromId = ctx.header(HEADER_FROM_ID_NAME());
            if (Utils.isEmpty(fromId)) {
                fromId = ctx.realIp();
                ctx.headerMap().put(HEADER_FROM_ID_NAME(), fromId);
            }

            return fromId;
        }
    }
}
