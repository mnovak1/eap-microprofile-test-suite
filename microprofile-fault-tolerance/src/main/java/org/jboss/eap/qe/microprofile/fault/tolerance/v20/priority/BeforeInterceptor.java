package org.jboss.eap.qe.microprofile.fault.tolerance.v20.priority;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@Priority(4000)
@BeforeFT
public class BeforeInterceptor {
    @Inject
    private InterceptorsContext context;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        context.getOrderQueue().add(this.getClass().getSimpleName());
        return ctx.proceed();
    }
}