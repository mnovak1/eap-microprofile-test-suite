package org.jboss.eap.qe.microprofile.fault.tolerance;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;

import java.util.regex.Pattern;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v10.HelloFallback;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v10.HelloService;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v10.HelloServlet;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v10.MyContext;
import org.jboss.eap.qe.microprofile.fault.tolerance.util.MicroProfileFaultToleranceServerConfiguration;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientProvider;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientRelatedException;
import org.jboss.eap.qe.microprofile.tooling.server.log.LogChecker;
import org.jboss.eap.qe.microprofile.tooling.server.log.ModelNodeLogChecker;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;

/**
 * Tests deploy/undeploy of MP FT applications in EAR archive
 */
@RunWith(Arquillian.class)
@RunAsClient
public class MultipleFaultToleranceModuleEarTest {

    private static final String DEPLOYMENT_EAR = "MultipleFaultToleranceModuleEarTest";
    private static final String FIRST_MODULE_NAME = "first-module";
    private static final String SECOND_MODULE_NAME = "second-module";
    private static final String FIRST_MODULE_URL = "http://localhost:8080/" + FIRST_MODULE_NAME;
    private static final String SECOND_MODULE_URL = "http://localhost:8080/" + SECOND_MODULE_NAME;
    private static final String METRICS_URL = "http://localhost:9990/metrics";

    @Deployment(name = DEPLOYMENT_EAR, testable = false)
    public static Archive<?> createDeploymentPackage() {

        final WebArchive firstModule = createModule(FIRST_MODULE_NAME, true, false);
        final WebArchive secondModule = createModule(SECOND_MODULE_NAME, false, true);

        String applicationXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<application xmlns=\"http://java.sun.com/xml/ns/javaee\"\n" +
                "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "             xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd\"\n"
                +
                "             version=\"6\">\n" +
                "    <initialize-in-order>true</initialize-in-order>\n" +
                "    <module>\n" +
                "        <web>\n" +
                "            <web-uri>" + FIRST_MODULE_NAME + ".war</web-uri>\n" +
                "            <context-root>" + FIRST_MODULE_NAME + "</context-root>\n" +
                "        </web>\n" +
                "    </module>\n" +
                "    <module>\n" +
                "        <web>\n" +
                "            <web-uri>" + SECOND_MODULE_NAME + ".war</web-uri>\n" +
                "            <context-root>" + SECOND_MODULE_NAME + "</context-root>\n" +
                "        </web>\n" +
                "    </module>\n" +
                "</application>";

        return ShrinkWrap.create(EnterpriseArchive.class)
                .addAsApplicationResource(new StringAsset(applicationXml), "application.xml")
                .addAsModule(firstModule)
                .addAsModule(secondModule);
    }

    public static WebArchive createModule(String moduleName, boolean faultToleranceTimeoutEnabled,
            boolean faultToleranceCircuitBreakerEnabled) {
        String mpConfig = "hystrix.command.default.execution.timeout.enabled=" + faultToleranceTimeoutEnabled + "\n" +
                "hystrix.command.default.circuitBreaker.enabled=" + faultToleranceCircuitBreakerEnabled;

        return ShrinkWrap.create(WebArchive.class, moduleName + ".war")
                .addClasses(HelloService.class, HelloServlet.class, HelloFallback.class, MyContext.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");
    }

    @BeforeClass
    public static void setup() throws ManagementClientRelatedException {
        MicroProfileFaultToleranceServerConfiguration.enableFaultTolerance();
    }

    /**
     * @tpTestDetails Deploy EAR with two MP FT modules. Both of them are the same (same classes/methods)
     * @tpPassCrit Verify that warning will be logged with information that Hystrix was already initialized by first
     *             application.
     * @tpSince EAP 7.4.0.CD19
     */
    @Test
    public void testDeployTwoModuleEarLogsWarning() throws Exception {
        get(FIRST_MODULE_URL + "/?operation=retry&context=foobar&fail=true").then()
                .assertThat()
                .body(containsString("Fallback Hello, context = "));
        try (final OnlineManagementClient client = ManagementClientProvider.onlineStandalone()) {
            final LogChecker logChecker = new ModelNodeLogChecker(client, 20, true);
            Assert.assertTrue("Log does not contain warning that Hystrix was already initialized by first module",
                    logChecker.logMatches(Pattern.compile(".*WFLYMPFTEXT0002.*")));
        }
    }

    /**
     * @tpTestDetails Deploy EAR with two MP FT modules. Both of them are the same (same classes/methods)
     * @tpPassCrit Verify that MP FT Metrics are the same for both of the deployments are summed.
     * @tpSince EAP 7.4.0.CD19
     */
    //    @Ignore("https://issues.redhat.com/browse/WFWIP-287")
    @Test
    public void testFaultToleranceMetricsAreSummedWithSameDeployments() {
        get(FIRST_MODULE_URL + "/?operation=timeout&context=foobar&fail=true").then()
                .assertThat()
                .body(containsString("Fallback Hello, context = foobar"));

        get(SECOND_MODULE_URL + "/?operation=timeout&context=foobar&fail=true").then()
                .assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
        get(METRICS_URL)
                .then()
                .assertThat()
                .body(containsString(
                        "application_ft_org_jboss_eap_qe_microprofile_fault_tolerance_deployments_v10_HelloService_timeout_invocations_total 2.0"));
    }

    /**
     * @tpTestDetails Deploy EAR with two MP FT modules. Both of them are the same (same classes/methods) however the first
     *                module configures Hystrix
     * @tpPassCrit Verify that Hystrix configured by first module
     * @tpSince EAP 7.4.0.CD19
     */
    @Ignore("https://issues.redhat.com/browse/WFWIP-287")
    @Test
    public void testFirstModuleConfiguresHystrix() {
        get(FIRST_MODULE_URL + "/?operation=retry&context=foobar&fail=true").then()
                .assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
        // circuit breaker is disabled
        get(SECOND_MODULE_URL + "/?operation=circuit-breaker&context=foobar&fail=true").then()
                .assertThat()
                .body(containsString("Hello from @CircuitBreaker method, context = foobar"));
    }

    @AfterClass
    public static void tearDown() throws ManagementClientRelatedException {
        MicroProfileFaultToleranceServerConfiguration.disableFaultTolerance();
    }

}
