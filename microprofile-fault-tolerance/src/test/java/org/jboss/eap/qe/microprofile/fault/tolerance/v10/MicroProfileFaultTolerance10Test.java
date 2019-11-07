package org.jboss.eap.qe.microprofile.fault.tolerance.v10;

import io.restassured.RestAssured;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
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
public class MicroProfileFaultTolerance10Test {

    public static final String APPLICATION_NAME = "MicroProfileFaultTolerance10Test";
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


//        File target = new File("/tmp/" + APPLICATION_NAME + ".war");
//        if (target.exists()) {
//            target.delete();
//        }
//        webArchive.as(ZipExporter.class).exportTo(target, true);
    }

    @Test
    @RunAsClient
    public void timeoutOk() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=timeout&context=foobar").then().assertThat()
                .body(containsString("Hello from @Timeout method, context = foobar"));
    }

    @Test
    @RunAsClient
    public void timeoutFailure() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
    }

    @Test
    @RunAsClient
    public void timeoutOkAsync() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=timeout").then().assertThat()
                .body(containsString("Hello from @Timeout method"));
    }

    @Test
    @RunAsClient
    public void timeoutFailureAsync() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=timeout&fail=true").then().assertThat()
                .body(containsString("Fallback Hello"));
    }

    @Test
    @RunAsClient
    public void retryOk() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=retry&context=foobar").then().assertThat()
                .body(containsString("Hello from @Retry method, context = foobar"));
    }

    @Test
    @RunAsClient
    public void retryFailure() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=retry&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
    }

    @Test
    @RunAsClient
    public void retryOkAsync() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=retry").then().assertThat()
                .body(containsString("Hello from @Retry method"));
    }

    @Test
    @RunAsClient
    public void retryFailureAsync() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=retry&fail=true").then().assertThat()
                .body(containsString("Fallback Hello"));
    }

    @Test
    @RunAsClient
    public void circuitBreakerOk() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/?operation=circuit-breaker&context=foobar").then().assertThat()
                .body(containsString("Hello from @CircuitBreaker method, context = foobar"));
    }

    @Test
    @RunAsClient
    public void circuitBreakerFailure() {
        testCircuitBreakerFailure(BASE_APPLICATION_URL + "/?operation=circuit-breaker&context=foobar",
                "Fallback Hello, context = foobar",
                "Hello from @CircuitBreaker method, context = foobar");
    }

    @Test
    @RunAsClient
    public void circuitBreakerOkAsync() {
        RestAssured.when().get(BASE_APPLICATION_URL + "/async?operation=circuit-breaker").then().assertThat()
                .body(containsString("Hello from @CircuitBreaker method"));
    }

    @Test
    @RunAsClient
    public void circuitBreakerFailureAsync() {
        testCircuitBreakerFailure(BASE_APPLICATION_URL + "/async?operation=circuit-breaker",
                "Fallback Hello",
                "Hello from @CircuitBreaker method");
    }

    private static void testCircuitBreakerFailure(String url, String expectedFallbackResponse, String expectedOkResponse) {
        // hystrix.command.default.circuitBreaker.requestVolumeThreshold
        int initialRequestsCount = 20;

        for (int i = 0; i < initialRequestsCount; i++) {
            RestAssured.when().get(url + "&fail=true").then().assertThat()
                    .body(containsString(expectedFallbackResponse));
        }

        // initialRequestsCount * hystrix.command.default.circuitBreaker.errorThresholdPercentage
        int failuresCount = 10;

        for (int i = 0; i < failuresCount; i++) {
            RestAssured.when().get(url).then().assertThat().body(containsString(expectedFallbackResponse));
        }

        // @CircuitBreaker.delay
        long circuitBreakerDelayMillis = 5000;
        long maxWaitTime = circuitBreakerDelayMillis + 1000;

        await().atMost(maxWaitTime, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            RestAssured.when().get(url).then().assertThat().body(containsString(expectedOkResponse));
        });
    }

    @Test
    @RunAsClient
    public void bulkheadOk() throws InterruptedException {
        Map<String, Integer> expectedResponses = new HashMap<>();
        expectedResponses.put("Hello from @Bulkhead method, context = foobar", 10);

        // 10 allowed invocations
        // 11 invocations would already trigger fallback
        testBulkhead(10, BASE_APPLICATION_URL + "/?operation=bulkhead&context=foobar", expectedResponses);
    }

    @Test
    @RunAsClient
    public void bulkheadFailure() throws InterruptedException {
        Map<String, Integer> expectedResponses = new HashMap<>();
        expectedResponses.put("Hello from @Bulkhead method, context = foobar", 10);
        expectedResponses.put("Fallback Hello, context = foobar", 20);

        // 30 = 10 allowed invocations + 20 not allowed invocations that lead to fallback
        // 31 invocations would already trigger fallback rejection
        testBulkhead(30, BASE_APPLICATION_URL + "/?operation=bulkhead&context=foobar&fail=true", expectedResponses);
    }

    @Test
    @RunAsClient
    public void bulkheadOkAsync() throws InterruptedException {
        Map<String, Integer> expectedResponses = new HashMap<>();
        expectedResponses.put("Hello from @Bulkhead method", 20);

        // 20 = 10 allowed invocations + 10 queued invocations
        // 21 invocations would already trigger fallback
        testBulkhead(20, BASE_APPLICATION_URL + "/async?operation=bulkhead", expectedResponses);
    }

    @Test
    @RunAsClient
    public void bulkheadFailureAsync() throws InterruptedException {
        Map<String, Integer> expectedResponses = new HashMap<>();
        expectedResponses.put("Hello from @Bulkhead method", 20);
        expectedResponses.put("Fallback Hello", 20);

        // 40 = 10 allowed invocations + 10 queued invocations + 20 not allowed invocations that lead to fallback
        // 41 invocations would already trigger fallback rejection
        testBulkhead(40, BASE_APPLICATION_URL + "/async?operation=bulkhead&fail=true", expectedResponses);
    }

    private static void testBulkhead(int parallelRequests, String url, Map<String, Integer> expectedResponses) throws InterruptedException {
        Set<String> violations = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Queue<String> seenResponses = new ConcurrentLinkedQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(parallelRequests);
        for (int i = 0; i < parallelRequests; i++) {
            executor.submit(() -> {
                try {
                    String response = RestAssured.when().get(url).asString();
                    seenResponses.add(response);
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
                violations.add("Expected to see " + expectedResponse.getValue() + " occurence(s) but seen " + count
                        + ": " + expectedResponse.getKey());
            }
        }

        assertThat(violations).isEmpty();
    }
}
