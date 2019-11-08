package org.jboss.eap.qe.microprofile.fault.tolerance.v10;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.stream.Stream;

@ApplicationScoped
public class HelloService {
    @Inject
    private MyContext context;

    @Timeout
    @Fallback(HelloFallback.class)
    public String timeout(boolean fail) throws InterruptedException {
        if (fail) {
            Thread.sleep(2000);
        }

        // SWARM-1930
/*
        return "Hello from @Timeout method, context = " + context.getValue();
*/
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

//    public String getConfig()   {
//        final Config config = ConfigProvider.getConfig();
//        StringBuilder stringBuilder = new StringBuilder();
//        for (ConfigSource configSource : config.getConfigSources()) {
//            stringBuilder.append(configSource + " = " + configSource.getName() + "\n");
//        }
//
//        for (String propertyName : config.getPropertyNames())    {
//            stringBuilder.append(propertyName + " = " + config.getValue(propertyName, String.class) + "\n");
//        }
//        return stringBuilder.toString();
//    }
}
