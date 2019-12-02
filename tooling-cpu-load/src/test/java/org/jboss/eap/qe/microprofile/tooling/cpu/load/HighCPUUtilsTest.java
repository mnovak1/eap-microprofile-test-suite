package org.jboss.eap.qe.microprofile.tooling.cpu.load;

import java.time.Duration;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.awaitility.Awaitility;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.eap.qe.microprofile.tooling.cpu.load.utils.ProcessUtils;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.arquillian.ArquillianContainerProperties;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.arquillian.ArquillianDescriptorWrapper;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientProvider;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;

/**
 * Tests tooling for HighCPUUtils. Test CPU on the system is exhausted.
 */
@RunWith(Arquillian.class)
@RunAsClient
public class HighCPUUtilsTest {

    private int cores = Runtime.getRuntime().availableProcessors();
    private int coresUnderLoad = cores > 1 ? cores - 1 : 1;

    @Test
    public void testCauseMaximumCPULoadOnProcess() throws Exception {
        Assert.assertTrue("This test must be run on RHEL or latest Fedora machines",
                System.getProperty("os.name").contains("Linux") && (System.getProperty("os.version").contains("el")
                        || System.getProperty("os.version").contains("fc2")));

        Process cpuLoadProcess = null;
        try (OnlineManagementClient client = ManagementClientProvider.onlineStandalone()) {
            HighCPUUtils.causeMaximumCPULoadOnContainer(ProcessUtils.getProcessId(client),
                    coresUnderLoad == 1 ? "0" : "0-" + coresUnderLoad, Duration.ofSeconds(10));

            ArquillianContainerProperties props = new ArquillianContainerProperties(
                    ArquillianDescriptorWrapper.getArquillianDescriptor());
            Awaitility.await("CPU load generator does not work and is not causing any CPU load.")
                    .atMost(org.awaitility.Duration.TEN_SECONDS)
                    .until(() -> getCpu(getConnection(props.getDefaultManagementAddress(),
                            props.getDefaultManagementPort())) > ((coresUnderLoad / (double) cores) - 0.02));
        } finally {
            if (cpuLoadProcess != null) {
                cpuLoadProcess.destroy();
            }
        }
    }

    private Double getCpu(MBeanServerConnection connection) throws Exception {
        Double value;
        value = (Double) connection.getAttribute(new ObjectName("java.lang:type=OperatingSystem"),
                "SystemCpuLoad");
        return value;
    }

    private MBeanServerConnection getConnection(String host, int port) throws Exception {
        String url = "service:jmx:remote+http://" + host + ":" + port;
        JMXServiceURL serviceURL = new JMXServiceURL(url);
        JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceURL);
        return jmxConnector.getMBeanServerConnection();
    }
}
