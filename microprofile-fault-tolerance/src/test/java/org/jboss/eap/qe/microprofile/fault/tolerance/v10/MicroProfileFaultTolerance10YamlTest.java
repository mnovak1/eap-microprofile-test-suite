package org.jboss.eap.qe.microprofile.fault.tolerance.v10;

import io.restassured.RestAssured;
import org.awaitility.Awaitility;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

@RunWith(Arquillian.class)
public class MicroProfileFaultTolerance10YamlTest {

    public static final String APPLICATION_NAME = "MicroProfileFaultTolerance10YamlTest";
    public static final String BASE_APPLICATION_URL = "http://localhost:8080/" + APPLICATION_NAME;

    @Deployment
    public static Archive<?> deployment() {
        String mpConfig = "hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds=2000\n" +
                "hystrix.command.default.execution.isolation.semaphore.maxConcurrentRequests=20\n" +
                "hystrix.threadpool.default.maximumSize=40\n" +
                "hystrix.threadpool.default.allowMaximumSizeToDivergeFromCoreSize=true\n";

        return ShrinkWrap.create(WebArchive.class, APPLICATION_NAME + ".war")
                .addPackages(true, HelloService.class.getPackage())
                .addClass(org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");
    }

    @Before
    public void setup() {
        Awaitility.setDefaultPollInterval(10, TimeUnit.MILLISECONDS);
    }

    @Test
    @RunAsClient
    public void circuitBreaker() throws IOException {
        // hystrix.command.default.circuitBreaker.requestVolumeThreshold
        int initialRequestsCount = 20;

        for (int i = 0; i < initialRequestsCount; i++) {
            RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=circuit-breaker&context=foobar").then().assertThat()
                    .body(containsString("Hello from @CircuitBreaker method, context = foobar"));
        }

        // @CircuitBreaker.delay
        long circuitBreakerDelayMillis = 5000;
        long maxWaitTime = circuitBreakerDelayMillis + 1000;

        await().atMost(maxWaitTime, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=circuit-breaker&context=foobar" + "&fail=true").then().assertThat()
                    .body(containsString("Fallback Hello, context = foobar"));
        });

        await().atMost(maxWaitTime, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=circuit-breaker&context=foobar").then().assertThat()
                    .body(containsString("Hello from @CircuitBreaker method, context = foobar"));
        });
    }
}
