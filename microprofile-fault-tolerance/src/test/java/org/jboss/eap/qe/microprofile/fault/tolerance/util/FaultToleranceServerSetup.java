package org.jboss.eap.qe.microprofile.fault.tolerance.util;

import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientProvider;

/**
 * Enables/Disables fault tolerance extension/subsystem for Arquillian in-container tests
 */
public class FaultToleranceServerSetup implements ServerSetupTask {

    @Override
    public void setup(ManagementClient managementClient, String containerId) throws Exception {
        MicroProfileFaultToleranceServerConfiguration.enableFaultTolerance(ManagementClientProvider.onlineStandalone(managementClient));
    }

    @Override
    public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
        MicroProfileFaultToleranceServerConfiguration.enableFaultTolerance(ManagementClientProvider.onlineStandalone(managementClient));
    }
}
