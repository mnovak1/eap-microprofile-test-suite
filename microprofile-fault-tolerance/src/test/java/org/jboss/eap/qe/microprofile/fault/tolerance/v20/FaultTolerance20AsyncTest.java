package org.jboss.eap.qe.microprofile.fault.tolerance.v20;

import io.restassured.RestAssured;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.eap.qe.microprofile.fault.tolerance.MicroProfileFaultToleranceTestParent;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v20.AsyncHelloService;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

@RunWith(Arquillian.class)
public class FaultTolerance20AsyncTest extends MicroProfileFaultToleranceTestParent {

    public static final String APPLICATION_NAME = FaultTolerance20AsyncTest.class.getSimpleName();
    public static final String BASE_APPLICATION_URL = "http://localhost:8080/" + APPLICATION_NAME;

    @Deployment
    public static Archive<?> deployment() {
        String mpConfig = "hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds=6000\n" +
                "hystrix.command.default.execution.isolation.semaphore.maxConcurrentRequests=20\n" +
                "hystrix.threadpool.default.maximumSize=40\n" +
                "hystrix.threadpool.default.allowMaximumSizeToDivergeFromCoreSize=true\n";
        return ShrinkWrap.create(WebArchive.class, APPLICATION_NAME + ".war")
                .addPackages(true, AsyncHelloService.class.getPackage())
                .addClasses(TimeoutException.class, FaultToleranceException.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");
    }

    @Test
    @RunAsClient
    public void timeoutOkCompletionStage() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/async?operation=timeout").then().assertThat()
                .body(containsString("Hello from @Timeout method"));
    }

    @Test
    @RunAsClient
    public void timeoutFailureCompletionStage() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/async?operation=timeout&fail=true").then().assertThat()
                .body(containsString("Fallback Hello"));
    }

    @Test
    @RunAsClient
    public void bulkheadTimeoutFailure() throws InterruptedException {
        Map<String, Integer> expectedResponses = new HashMap<>();
        expectedResponses.put("Hello from @Bulkhead @Timeout method", 0);
        expectedResponses.put("Fallback Hello", 40);
        // timeout takes effect, there will be 40 fallbacks (project-defaults.yml.. maximumSize: 40)
        // 41 invocations would already trigger fallback rejection
        // no matter @Bulkhead has e.g. value = 15 and waitingTaskQueue = 15
        testBulkhead(40, BASE_APPLICATION_URL + "/async?operation=bulkhead-timeout&fail=true", expectedResponses);
    }

    @Test
    @RunAsClient
    public void bulkheadTimeoutRetryOK() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/async?operation=bulkhead-timeout-retry").then().assertThat()
                .body(containsString("Hello from @Bulkhead @Timeout @Retry method"));
    }

    @Test
    @RunAsClient
    public void bulkheadTimeoutRetryFailure() throws InterruptedException {
        Map<String, Integer> expectedResponses = new HashMap<>();
        expectedResponses.put("Hello from @Bulkhead @Timeout @Retry method", 0);
        expectedResponses.put("Fallback Hello", 40);

        testBulkhead(40, BASE_APPLICATION_URL + "/async?operation=bulkhead-timeout-retry&fail=true", expectedResponses);
    }

    private static void testBulkhead(int parallelRequests, String url, Map<String, Integer> expectedResponses) throws InterruptedException {
        Set<String> violations = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Queue<String> seenResponses = new ConcurrentLinkedQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(parallelRequests);
        for (int i = 0; i < parallelRequests; i++) {
            executor.submit(() -> {
                try {
                    seenResponses.add(RestAssured.when().get(url).asString());
                } catch (Exception e) {
                    violations.add("Unexpected exception: " + e.getMessage());
                }
            });
        }
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        for (String seenResponse : seenResponses) {
            if (!expectedResponses.containsKey(seenResponse)) {
                violations.add("Unexpected response: " + seenResponse);
            }
        }
        for (Map.Entry<String, Integer> expectedResponse : expectedResponses.entrySet()) {
            int count = 0;
            for (String seenResponse : seenResponses) {
                if (expectedResponse.getKey().equals(seenResponse)) {
                    count++;
                }
            }
            if (count != expectedResponse.getValue()) {
                violations.add("Expected to see " + expectedResponse.getValue() + " occurrence(s) but seen " + count
                                       + ": " + expectedResponse.getKey());
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    @RunAsClient
    public void retryCircuitBreakerFailure() throws IOException, InterruptedException {
        testCircuitBreakerFailure(BASE_APPLICATION_URL + "/async?operation=retry-circuit-breaker",
                                  "Fallback Hello",
                                  "Hello from @Retry @CircuitBreaker method");
    }

    @Test
    @RunAsClient
    public void retryCircuitBreakerTimeoutOK() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/async?operation=retry-circuit-breaker-timeout").then().assertThat()
                .body(containsString("Hello from @Retry @CircuitBreaker @Timeout method"));
    }

    @Test
    @RunAsClient
    public void retryCircuitBreakerTimeoutFailure() throws IOException, InterruptedException {
        testCircuitBreakerFailure(BASE_APPLICATION_URL + "/async?operation=retry-circuit-breaker-timeout",
                                  "Fallback Hello",
                                  "Hello from @Retry @CircuitBreaker @Timeout method");
    }

    private static void testCircuitBreakerFailure(String url, String expectedFallbackResponse, String expectedOkResponse) throws IOException, InterruptedException {
        // call 20x fail URL, circuit is OPEN
        for (int i = 0; i < 20; i++) {
            RestAssured.when().get(url + "&fail=true").then().assertThat().body(containsString(expectedFallbackResponse));
        }
        // call 10x correct URL on opened circuit -> still returns fallback response
        for (int i = 0; i < 10; i++) {
            RestAssured.when().get(url).then().assertThat().body(containsString(expectedFallbackResponse));
        }
        // the window of 20 calls now contains 10 fail and 10 correct responses, this equals 0.5 failureRatio
        // @CircuitBreaker.delay is 5 seconds default, then circuit is CLOSED and OK response is returned
        Thread.sleep(5000L);
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            RestAssured.when().get(url).then().assertThat().body(containsString(expectedOkResponse));
        });
    }

    @Test
    @RunAsClient
    public void retryTimeout() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/async?operation=retry-timeout&fail=true").then().assertThat()
                .body(containsString("Fallback Hello"));
    }

    @Test
    @RunAsClient
    public void timeoutCircuitBreakerOK() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/async?operation=timeout-circuit-breaker").then().assertThat()
                .body(containsString("Hello from @Timeout @CircuitBreaker method"));
    }

    @Test
    @RunAsClient
    public void timeoutCircuitBreakerFailure() throws IOException, InterruptedException {
        testCircuitBreakerFailure(BASE_APPLICATION_URL + "/async?operation=timeout-circuit-breaker",
                                  "Fallback Hello",
                                  "Hello from @Timeout @CircuitBreaker method");
    }
}
