package org.jboss.eap.qe.microprofile.fault.tolerance;

import static io.restassured.RestAssured.get;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.load.LoadService;
import org.jboss.eap.qe.microprofile.fault.tolerance.util.MicroProfileFaultToleranceServerConfiguration;
import org.jboss.eap.qe.microprofile.tooling.cpu.load.HighCPUUtils;
import org.jboss.eap.qe.microprofile.tooling.cpu.load.utils.ProcessUtils;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientProvider;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientRelatedException;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;

/**
 * Test class verifies correct behaviour of Fault Tolerance under high CPU load.
 */

@RunAsClient
@RunWith(Arquillian.class)
public class CpuLoadTest {

    private static final String APPLICATION_NAME = "CpuLoadTest";
    private static final String APPLICATION_URL = "http://localhost:8080/" + APPLICATION_NAME;
    private static final Duration CPU_LOAD_DURATION = Duration.ofSeconds(10);

    @Deployment(testable = false)
    public static Archive<?> createFirstWarDeployment() {
        String mpConfig = "hystrix.command.default.execution.timeout.enabled=true";

        return ShrinkWrap.create(WebArchive.class, APPLICATION_NAME + ".war")
                .addPackage(LoadService.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");
    }

    @BeforeClass
    public static void serverSetup() throws ManagementClientRelatedException {
        MicroProfileFaultToleranceServerConfiguration.enableFaultTolerance();
    }

    /**
     * @tpTestDetails Deploy MP FT application and call service with @Timeout annotation. During execution
     *                of this method cause 10s CPU load.
     * @tpPassCrit Verify that method throws {@link org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException}
     * @tpSince EAP 7.4.0.CD19
     */
    @Test
    public void testCpuLoadWithTimeout() throws Exception {

        Assume.assumeTrue(HighCPUUtils.canRunOnThisEnvironment());

        // call service which takes 5 sec
        Future<String> underCpuLoadCall = Executors.newSingleThreadExecutor().submit(
                () -> get(APPLICATION_URL + "/?operation=timeout").body().asString());

        try (OnlineManagementClient client = ManagementClientProvider.onlineStandalone()) {
            causeCpuLoadOnServer(client, CPU_LOAD_DURATION);
        }

        Assertions.assertThat(underCpuLoadCall.get())
                .contains("org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException");
    }

    /**
     * @tpTestDetails Deploy MP FT application and call service with @Retry annotation. During execution
     *                of this method cause 10s CPU load.
     * @tpPassCrit Verify that method throws Exception once max retry is reached.
     * @tpSince EAP 7.4.0.CD19
     */
    @Test
    public void testCpuLoadWithRetry() throws Exception {

        Assume.assumeTrue(HighCPUUtils.canRunOnThisEnvironment());

        Future<String> underCpuLoadCall = Executors.newSingleThreadExecutor().submit(
                () -> get(APPLICATION_URL + "/?operation=retry").body().asString());

        try (OnlineManagementClient client = ManagementClientProvider.onlineStandalone()) {
            causeCpuLoadOnServer(client, CPU_LOAD_DURATION);
        }

        Assertions.assertThat(underCpuLoadCall.get()).contains("Exception from @Retry method.");
    }

    /**
     * @tpTestDetails Deploy MP FT application and call service with @Fallback annotation. During execution
     *                of this method cause 10s CPU load.
     * @tpPassCrit Verify that @Fallback method was called
     * @tpSince EAP 7.4.0.CD19
     */
    @Test
    public void testCpuLoadWithFallback() throws Exception {

        Assume.assumeTrue(HighCPUUtils.canRunOnThisEnvironment());

        Future<String> underCpuLoadCall = Executors.newSingleThreadExecutor().submit(
                () -> get(APPLICATION_URL + "/?operation=timeoutWithFallback").body().asString());

        try (OnlineManagementClient client = ManagementClientProvider.onlineStandalone()) {
            causeCpuLoadOnServer(client, CPU_LOAD_DURATION);
        }

        Assertions.assertThat(underCpuLoadCall.get()).contains("Hello from @Fallback method");
    }

    /**
     * Causes 100% CPU on load on server
     *
     * @param client management client
     * @param duration how to cause CPU load
     */
    private void causeCpuLoadOnServer(OnlineManagementClient client, Duration duration) throws Exception {
        Process highCpuLoader = null;
        long cpuLoaderStartTime;
        try {
            String cpuToBind = "0";
            highCpuLoader = HighCPUUtils.causeMaximumCPULoadOnContainer(
                    ProcessUtils.getProcessId(client), cpuToBind, duration);
            // wait for CPU load generator to finish
            highCpuLoader.waitFor(duration.toMillis() + 5000, TimeUnit.MILLISECONDS);
        } finally {
            if (highCpuLoader != null) {
                highCpuLoader.destroy();
            }
        }
    }

    @AfterClass
    public static void tearDown() throws ManagementClientRelatedException {
        MicroProfileFaultToleranceServerConfiguration.disableFaultTolerance();
    }
}
