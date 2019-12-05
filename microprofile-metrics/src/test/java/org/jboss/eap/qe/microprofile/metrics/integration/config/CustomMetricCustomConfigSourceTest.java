package org.jboss.eap.qe.microprofile.metrics.integration.config;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.eap.qe.microprofile.metrics.integration.ModuleUtil;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientProvider;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.commands.modules.RemoveModule;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;

/**
 * MP Config property is provided by ConfigSource.
 */
@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(CustomMetricCustomConfigSourceTest.SetupTask.class)
public class CustomMetricCustomConfigSourceTest extends CustomMetricDynamicBaseTest {
    private static final String PROPERTY_FILENAME = "custom-metric.properties";
    File propertyFile = new File(CustomMetricCustomConfigSourceTest.class.getResource(PROPERTY_FILENAME).getFile());
    private byte[] bytes;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        WebArchive webArchive = ShrinkWrap
                .create(WebArchive.class, CustomMetricCustomConfigSourceTest.class.getSimpleName() + ".war")
                .addClasses(CustomCounterIncrementProvider.class, CustomCounterMetric.class, CustomMetricService.class,
                        CustomMetricApplication.class, CustomMetricAppInitializer.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return webArchive;
    }

    @Before
    public void backup() throws IOException {
        bytes = FileUtils.readFileToByteArray(propertyFile);
    }

    @After
    public void restore() throws IOException {
        FileUtils.writeByteArrayToFile(propertyFile, bytes);
    }

    void setConfigProperties(int increment) throws IOException {
        FileUtils.writeStringToFile(propertyFile, INCREMENT_CONFIG_PROPERTY + "=" + Integer.toString(increment));
    }

    /**
     * Setup a microprofile-config-smallrye subsystem to obtain values from {@link CustomConfigSource} provided by
     * {@link CustomConfigSourceProvider}
     */
    static class SetupTask implements ServerSetupTask {
        private static final String TEST_MODULE_NAME = "test.custom-config-source";

        @Override
        public void setup(ManagementClient managementClient, String s) throws Exception {
            OnlineManagementClient client = ManagementClientProvider.onlineStandalone();
            client.execute(String.format("/system-property=%s:add(value=%s)", CustomConfigSource.FILEPATH_PROPERTY,
                    CustomMetricCustomConfigSourceProviderTest.SetupTask.class.getResource(PROPERTY_FILENAME).getFile()));
            ModuleUtil.setupModule(client, new File(SetupTask.class.getResource("configSourceModule.xml").toURI()),
                    TEST_MODULE_NAME, "config-source", CustomConfigSource.class);
            client.execute(String.format(
                    "/subsystem=microprofile-config-smallrye/config-source=cs-from-class:add(class={module=%s, name=%s})",
                    TEST_MODULE_NAME, CustomConfigSource.class.getName()));
        }

        @Override
        public void tearDown(ManagementClient managementClient, String s) throws Exception {
            OnlineManagementClient client = ManagementClientProvider.onlineStandalone();
            client.execute(String.format("/system-property=%s:remove", CustomConfigSource.FILEPATH_PROPERTY));
            client.execute("/subsystem=microprofile-config-smallrye/config-source=cs-from-class:remove");
            ManagementClientProvider.onlineStandalone().apply(new RemoveModule(TEST_MODULE_NAME));
        }
    }
}
