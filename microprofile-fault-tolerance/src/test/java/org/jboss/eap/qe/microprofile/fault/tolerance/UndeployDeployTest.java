package org.jboss.eap.qe.microprofile.fault.tolerance;

import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.eap.qe.microprofile.fault.tolerance.deployments.v10.HelloService;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;

@RunAsClient
@RunWith(Arquillian.class)
public class UndeployDeployTest extends MicroProfileFaultToleranceTestParent {

    public static final String FIRST_DEPLOYMENT = "UndeployDeployTest-first-deployment";
    public static final String SECOND_DEPLOYMENT = "UndeployDeployTestsecond-deployment";
    public static final String FIRST_APPLICATION_URL = "http://localhost:8080/" + FIRST_DEPLOYMENT;
    public static final String SECOND_APPLICATION_URL = "http://localhost:8080/" + SECOND_DEPLOYMENT;
    public static final String METRICS_URL = "http://localhost:9990/metrics";

    @ArquillianResource
    private Deployer deployer;

    @Deployment(name = FIRST_DEPLOYMENT, managed = true)
    public static Archive<?> createFirstDeployment() {
        String mpConfig = "hystrix.command.default.execution.timeout.enabled=true";

        WebArchive webArchive = ShrinkWrap.create(WebArchive.class,  FIRST_DEPLOYMENT + ".war")
                .addPackages(true, HelloService.class.getPackage())
                .addClass(TimeoutException.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");

        return webArchive;
    }

    @Deployment(name = SECOND_DEPLOYMENT, managed = true)
    public static Archive<?> createSecondDeployment() {
        String mpConfig = "hystrix.command.default.execution.timeout.enabled=false";

        WebArchive webArchive = ShrinkWrap.create(WebArchive.class, SECOND_DEPLOYMENT + ".war")
                .addPackages(true, HelloService.class.getPackage())
                .addClass(TimeoutException.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");

        return webArchive;
    }

    @Before
    public void reload() throws Exception {
        // reload server to stop hystrix
        try (OnlineManagementClient client = getOnlineManagementClient())   {
            Administration administration = new Administration(client);
            administration.reload();
        }
    }

    @Test
    public void testUndeployDeployChangesHystrixConfiguration() {
        deployer.deploy(FIRST_DEPLOYMENT);
        get(FIRST_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
        deployer.undeploy(FIRST_DEPLOYMENT);

        // make sure it's undeployed
        get(FIRST_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat().statusCode(404);

        deployer.deploy(SECOND_DEPLOYMENT);
        get(SECOND_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Hello from @Timeout method, context = foobar"));
        deployer.undeploy(SECOND_DEPLOYMENT);
    }

    @Test
    public void testSecondDeploymentDoesNotChangeConfigurationAndWarningWasLogged() {
        deployer.deploy(FIRST_DEPLOYMENT);
        deployer.deploy(SECOND_DEPLOYMENT);

        // check log contains warning
        // todo - wait for tooling

        get(FIRST_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
        // timeout is still working even though 2nd deployment has disabled it
        get(SECOND_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));

        deployer.undeploy(FIRST_DEPLOYMENT);
        deployer.undeploy(SECOND_DEPLOYMENT);
    }

    @Test
    public void testFaultToleranceMetricsAreSummedWithSameDeployments() {
        deployer.deploy(FIRST_DEPLOYMENT);
        deployer.deploy(SECOND_DEPLOYMENT);

        get(FIRST_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
        // timeout is still working even though 2nd deployment has disabled it
        get(SECOND_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));

        get(METRICS_URL).then().assertThat()
                .body(containsString("application_ft_org_jboss_eap_qe_microprofile_fault_tolerance_v10_HelloService_timeout_invocations_total 2.0"));

        deployer.undeploy(FIRST_DEPLOYMENT);
        deployer.undeploy(SECOND_DEPLOYMENT);
    }

    @Test
    public void testTwoMPFTDeploymentsUndeploySecondDoesNotRestartHystrix() {

        deployer.deploy(FIRST_DEPLOYMENT);
        deployer.deploy(SECOND_DEPLOYMENT);

        get(FIRST_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));

        deployer.undeploy(FIRST_DEPLOYMENT);

        get(SECOND_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
        deployer.undeploy(SECOND_DEPLOYMENT);
    }

    @After
    public void undeploy()  {
        // in case of test failure, clean up deployments
        deployer.undeploy(FIRST_DEPLOYMENT);
        deployer.undeploy(SECOND_DEPLOYMENT);
    }
}
