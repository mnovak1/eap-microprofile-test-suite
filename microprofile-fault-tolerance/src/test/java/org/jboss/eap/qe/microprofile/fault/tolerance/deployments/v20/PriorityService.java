package org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v20;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v20.priority.AfterFT;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v20.priority.BeforeFT;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v20.priority.InterceptorsContext;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class PriorityService {
    @Inject
    private InterceptorsContext context;

    @Retry(maxRetries = 1)
    @Fallback(fallbackMethod = "processFallback")
    @AfterFT
    @BeforeFT
    public String retryFallback(boolean fail) {
        context.getOrderQueue().add("Inside method");
        if (fail) {
            throw new TimeoutException("Simulated TimeoutException");
        }
        String queueContent = context.getOrderQueue().toString();
        return "Hello from method: " + queueContent;
    }

    private String processFallback(boolean fail) {
        context.getOrderQueue().add("processFallback");
        String queueContent = context.getOrderQueue().toString();
        return "Fallback Hello: " + queueContent;
    }
}