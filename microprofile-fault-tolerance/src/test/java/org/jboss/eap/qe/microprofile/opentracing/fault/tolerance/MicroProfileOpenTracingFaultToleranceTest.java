package org.jboss.eap.qe.microprofile.opentracing.fault.tolerance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.opentracing.Tracer;
import io.restassured.RestAssured;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.eap.qe.ts.common.docker.Docker;
import org.jboss.eap.qe.ts.common.docker.DockerContainers;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@RunAsClient
@RunWith(Arquillian.class)
public class MicroProfileOpenTracingFaultToleranceTest {

    public static final String APPLICATION_NAME = "MicroProfileOpenTracingFaultToleranceTest";
    public static final String BASE_APPLICATION_URL = "http://localhost:8080/" + APPLICATION_NAME;

//    @Rule // /todo update version to latest or something newer
//    public GenericContainer jaegerContainer =  new GenericContainer<>("jaegertracing/all-in-one:1.11")
//            .withExposedPorts(5775, 5778, 6831, 6832, 14267, 14250, 14268, 16686)
//            .withNetwork(Network.newNetwork())
//            .withEnv("COLLECTOR_ZIPKIN_HTTP_PORT", "9411")
//            .waitingFor(Wait.forHttp("/search").forPort(16686))
//            .withCommand("--reporter.grpc.host-port=localhost:14250")
//            .withStartupTimeout(Duration.ofSeconds(180));

    @ClassRule
    public static Docker jaegerContainer=DockerContainers.jaeger();

    @Deployment(testable = false)
    public static Archive<?> deployment() {
        String mpConfig = "service.name=test-traced-service\n" +
                "sampler.param=1\n" +
                "sampler.type=const\n";
        WebArchive war = ShrinkWrap.create(WebArchive.class, APPLICATION_NAME + ".war")
                .addPackage(RestApplication.class.getPackage())
//                .addPackage(Tracer.class.getPackage())
//                .addPackage(io.opentracing.ScopeManager.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");
        File target = new File("/tmp/" + APPLICATION_NAME + ".war");
        if (target.exists()) {
            target.delete();
        }
        war.as(ZipExporter.class).exportTo(target, true);
        return war;
    }

    @Test
    @RunAsClient
    public void start() throws IOException {
//        jaegerContainer.withLogConsumer((it) -> {
//            System.out.println(it);
//        });
//        jaegerContainer.start();

    }

//    @Test
//    @InSequence(1)
//    @RunAsClient
//    public void applicationRequest_invocation() throws IOException {
//        RestAssured.when().get(BASE_APPLICATION_URL + "/rest/hello").then().assertThat()
//                .body(containsString("Hello from fallback"));
//    }
//
//    @Test
//    @InSequence(2)
//    @RunAsClient
//    public void applicationRequest_asyncInvocation() throws IOException {
//        RestAssured.when().get(BASE_APPLICATION_URL + "/rest/helloAsync").then().assertThat()
//                .body(containsString("Hello from async fallback"));
//
//    }
//
//    @Test
//    @InSequence(3)
//    @RunAsClient
//    public void traces() {
//        String[] suffixes = {"", "Async"};
//        try {
//            Thread.sleep(6000000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        // the tracer inside the application doesn't send traces to the Jaeger server immediately,
//        // they are batched, so we need to wait a bit
//        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
//            String response = RestAssured.when().get("http://localhost:16686/api/traces?service=test-traced-service").asString();
//            JsonObject json = new JsonParser().parse(response).getAsJsonObject();
//            assertThat(json.has("data")).isTrue();
//            JsonArray data = json.getAsJsonArray("data");
//            assertThat(data.size()).isEqualTo(suffixes.length);
//            for (int i = 0; i < suffixes.length; i++) {
//                String suffix = suffixes[i];
//                JsonObject trace = data.get(i).getAsJsonObject();
//                assertThat(trace.has("spans")).isTrue();
//                JsonArray spans = trace.getAsJsonArray("spans");
//                assertThat(spans).hasSize(4);
//                assertThat(spans).anySatisfy(element -> {
//                    JsonObject span = element.getAsJsonObject();
//                    assertThat(span.get("operationName").getAsString())
//                            .isEqualTo("GET:org.wildfly.swarm.ts.microprofile.opentracing.fault.tolerance.HelloResource.get" + suffix);
//                    assertThat(span.has("tags")).isTrue();
//                    JsonArray tags = span.getAsJsonArray("tags");
//                    for (JsonElement tagElement : tags) {
//                        JsonObject tag = tagElement.getAsJsonObject();
//                        switch (tag.get("key").getAsString()) {
//                            case "http.method":
//                                assertThat(tag.get("value").getAsString()).isEqualTo("GET");
//                                break;
//                            case "http.url":
//                                assertThat(tag.get("value").getAsString()).isEqualTo(BASE_APPLICATION_URL + "/rest/hello" + suffix);
//                                break;
//                            case "http.status.code":
//                                assertThat(tag.get("value").getAsInt()).isEqualTo(200);
//                                break;
//                            case "component":
//                                assertThat(tag.get("value").getAsString()).isEqualTo("jaxrs");
//                                break;
//                            case "span.kind":
//                                assertThat(tag.get("value").getAsString()).isEqualTo("server");
//                                break;
//                        }
//                    }
//                });
//                assertThat(spans).anySatisfy(element -> {
//                    JsonObject span = element.getAsJsonObject();
//                    assertThat(span.get("operationName").getAsString()).isEqualTo("hello" + suffix);
//                    assertThat(span.has("logs")).isTrue();
//                    JsonArray logs = span.getAsJsonArray("logs");
//                    assertThat(logs).hasSize(1);
//                    JsonObject log = logs.get(0).getAsJsonObject();
//                    assertThat(log.getAsJsonArray("fields").get(0).getAsJsonObject().get("value").getAsString())
//                            .isEqualTo("attempt 0");
//                });
//                assertThat(spans).anySatisfy(element -> {
//                    JsonObject span = element.getAsJsonObject();
//                    assertThat(span.get("operationName").getAsString()).isEqualTo("hello" + suffix);
//                    assertThat(span.has("logs")).isTrue();
//                    JsonArray logs = span.getAsJsonArray("logs");
//                    assertThat(logs).hasSize(1);
//                    JsonObject log = logs.get(0).getAsJsonObject();
//                    assertThat(log.getAsJsonArray("fields").get(0).getAsJsonObject().get("value").getAsString())
//                            .isEqualTo("attempt 1");
//                });
//                assertThat(spans).anySatisfy(element -> {
//                    JsonObject span = element.getAsJsonObject();
//                    assertThat(span.get("operationName").getAsString()).isEqualTo("fallback" + suffix);
//                });
//            }
//        });
//    }
}
