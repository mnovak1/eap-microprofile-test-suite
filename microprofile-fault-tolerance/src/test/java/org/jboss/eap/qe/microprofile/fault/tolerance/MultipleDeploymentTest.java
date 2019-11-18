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
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

import java.io.File;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;

@RunAsClient
@RunWith(Arquillian.class)
public class MultipleDeploymentTest extends MicroProfileFaultToleranceTestParent {

    public static final String FIRST_DEPLOYMENT_JAR = "first-deployment-jar";
    public static final String SECOND_DEPLOYMENT_JAR = "second-deployment-jar";
    public static final String FIRST_DEPLOYMENT_WAR = "first-deployment-war";
    public static final String SECOND_DEPLOYMENT_WAR = "second-deployment-war";
    public static final String FIRST_APPLICATION_URL = "http://localhost:8080/" + FIRST_DEPLOYMENT_JAR;
    public static final String SECOND_APPLICATION_URL = "http://localhost:8080/" + SECOND_DEPLOYMENT_JAR;
    public static final String METRICS_URL = "http://localhost:9990/metrics";

    @ArquillianResource
    private Deployer deployer;

    @Deployment(name = FIRST_DEPLOYMENT_WAR, managed = true)
    public static Archive<?> createFirstWarDeployment() {
        String mpConfig = "hystrix.command.default.execution.timeout.enabled=true";

        WebArchive webArchive = ShrinkWrap.create(WebArchive.class,  FIRST_DEPLOYMENT_WAR + ".war")
                .addPackage(HelloService.class.getPackage())
                .addClass(TimeoutException.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");

        return webArchive;
    }

    @Deployment(name = SECOND_DEPLOYMENT_WAR, managed = true)
    public static Archive<?> createSecondWarDeployment() {
        String mpConfig = "hystrix.command.default.execution.timeout.enabled=false";

        WebArchive webArchive = ShrinkWrap.create(WebArchive.class, SECOND_DEPLOYMENT_WAR + ".war")
                .addPackage( HelloService.class.getPackage())
                .addClass(TimeoutException.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");

        return webArchive;
    }

    @Deployment(name = FIRST_DEPLOYMENT_JAR, managed = true)
    public static Archive<?> createFirstJarDeployment() {
        String mpConfig = "hystrix.command.default.execution.timeout.enabled=true";

        JavaArchive jarArchive = ShrinkWrap.create(JavaArchive.class, FIRST_DEPLOYMENT_JAR + ".jar")
                .addPackages(false, HelloService.class.getPackage())
                .addClass(TimeoutException.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");
        File file = new File("/tmp/" + FIRST_DEPLOYMENT_JAR + ".jar");

        new ZipExporterImpl(jarArchive).exportTo(file, true);

        return jarArchive;
    }

    @Deployment(name = SECOND_DEPLOYMENT_JAR, managed = true)
    public static Archive<?> createSecondJarDeployment() {
        String mpConfig = "hystrix.command.default.execution.timeout.enabled=false";

        JavaArchive jarArchive = ShrinkWrap.create(JavaArchive.class, SECOND_DEPLOYMENT_JAR + ".jar")
                .addPackages(true, HelloService.class.getPackage())
                .addClass(TimeoutException.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(new StringAsset(mpConfig), "microprofile-config.properties");

        return jarArchive;
    }

    @Before
    public void reload() throws Exception {
        // reload server to stop hystrix
        try (OnlineManagementClient client = getOnlineManagementClient()) {
            Administration administration = new Administration(client);
            administration.reload();
        }
    }

    // todo
    @Test
    public void testDeployTwoJarsLogsWarning() {
        deployer.deploy(FIRST_DEPLOYMENT_JAR);
        deployer.deploy(SECOND_DEPLOYMENT_JAR);
        get(FIRST_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
        get(SECOND_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Hello from @Timeout method, context = foobar"));

        // todo add check for warning in log
        deployer.undeploy(FIRST_DEPLOYMENT_JAR);
        deployer.undeploy(SECOND_DEPLOYMENT_JAR);
    }

    @Test
    public void testDeployTwoWarsLogsWarning() {
        deployer.deploy(FIRST_DEPLOYMENT_WAR);
        deployer.deploy(SECOND_DEPLOYMENT_WAR);
        get(FIRST_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Fallback Hello, context = foobar"));
        get(SECOND_APPLICATION_URL + "/?operation=timeout&context=foobar&fail=true").then().assertThat()
                .body(containsString("Hello from @Timeout method, context = foobar"));

        // todo add check for warning in log
        deployer.undeploy(FIRST_DEPLOYMENT_WAR);
        deployer.undeploy(SECOND_DEPLOYMENT_WAR);
    }

    @After
    public void undeploy() {
        // in case of test failure, clean up deployments
        deployer.undeploy(FIRST_DEPLOYMENT_JAR);
        deployer.undeploy(SECOND_DEPLOYMENT_JAR);
        deployer.undeploy(FIRST_DEPLOYMENT_WAR);
        deployer.undeploy(SECOND_DEPLOYMENT_WAR);
    }
}

