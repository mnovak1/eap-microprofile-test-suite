package org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v10;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@ApplicationScoped
public class HelloService {

    // make sure retry was called at least 2 times
    private static final CountDownLatch infiniteRetryCalled = new CountDownLatch(2);

    @Inject
    private MyContext context;

    @Timeout
    @Fallback(HelloFallback.class)
    public String timeout(boolean fail) throws InterruptedException {
        if (fail) {
            Thread.sleep(2000);
        }
        // SWARM-1930
        return "Hello from @Timeout method, context = foobar";
    }

    @Retry
    @Fallback(HelloFallback.class)
    public String retry(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("Simulated IO error");
        }

        return "Hello from @Retry method, context = " + context.getValue();
    }

    @Retry(maxRetries = -1, delay = 100)
    public String infiniteRetry() throws IOException {
        infiniteRetryCalled.countDown();
        if (Boolean.valueOf(System.getProperty("exitInfiniteRetry", "false"))) {
            return "Hello from infinite @Retry method, context = " + context.getValue();

        }
        throw new IOException("Keep retrying...");
    }

    public boolean isInfiniteRetryInProgress() throws InterruptedException {
        return infiniteRetryCalled.await(10, TimeUnit.SECONDS);
    }

    @CircuitBreaker
    @Fallback(HelloFallback.class)
    public String circuitBreaker(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("Simulated IO error");
        }

        return "Hello from @CircuitBreaker method, context = " + context.getValue();
    }

    @Bulkhead
    @Fallback(HelloFallback.class)
    public String bulkhead(boolean fail) throws InterruptedException {
        if (fail) {
            Thread.sleep(2000);
        }

        return "Hello from @Bulkhead method, context = " + context.getValue();
    }
}
