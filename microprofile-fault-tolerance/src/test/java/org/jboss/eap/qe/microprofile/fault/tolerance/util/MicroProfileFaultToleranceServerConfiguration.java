package org.jboss.eap.qe.microprofile.fault.tolerance.util;

import java.io.IOException;

import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientHelper;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientProvider;
import org.jboss.eap.qe.microprofile.tooling.server.configuration.creaper.ManagementClientRelatedException;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.operations.Address;
import org.wildfly.extras.creaper.core.online.operations.OperationException;
import org.wildfly.extras.creaper.core.online.operations.Operations;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

/**
 * Utility class enabling and disabling MP Fault Tolerance (which is disabled by default)
 */
public class MicroProfileFaultToleranceServerConfiguration {

    private static final Address MICROPROFILE_FAULT_TOLERANCE_EXTENSION_ADDRESS = Address
            .extension("org.wildfly.extension.microprofile.fault-tolerance-smallrye");
    private static final Address MICROPROFILE_FAULT_TOLERANCE_SUBSYSTEM_ADDRESS = Address
            .subsystem("microprofile-fault-tolerance-smallrye");

    /**
     * Enable fault tolerance extension and subsystem.
     *
     * @throws ManagementClientRelatedException Wraps exceptions thrown by the internal operation executed by
     *                                          {@link OnlineManagementClient} API
     */
    public static void enableFaultTolerance() throws ManagementClientRelatedException {
        try (OnlineManagementClient client = ManagementClientProvider.onlineStandalone()) {
            enableFaultTolerance(client);
        } catch (Exception ex) {
            throw new ManagementClientRelatedException(
                    "Enabling Fault Tolerance failed. This might crash other tests.", ex);
        }
    }

    /**
     * Enable fault tolerance extension and subsystem.
     *
     * @param client {@link OnlineManagementClient} instance used to execute the command
     * @throws ManagementClientRelatedException Wraps exceptions thrown by the internal operation executed by
     *         {@link OnlineManagementClient} API
     */
    public static void enableFaultTolerance(OnlineManagementClient client) throws ManagementClientRelatedException {
        if (!faultToleranceExtensionExists(client)) {
            ManagementClientHelper.executeCliCommand(client, MICROPROFILE_FAULT_TOLERANCE_EXTENSION_ADDRESS + ":add()");
        }
        if (!faultToleranceSubsystemExists(client)) {
            ManagementClientHelper.executeCliCommand(client, MICROPROFILE_FAULT_TOLERANCE_SUBSYSTEM_ADDRESS + ":add()");
        }
        try {
            new Administration(client).reloadIfRequired();
        } catch (Exception ex) {
            throw new ManagementClientRelatedException(
                    "Enabling Fault Tolerance subsystem failed. This might crash other tests.", ex);
        }
    }

    /**
     * Disable Fault Tolerance subsystem and extension
     *
     * @throws ManagementClientRelatedException Wraps exceptions thrown by the internal operation executed by
     *                                          {@link OnlineManagementClient} API
     */
    public static void disableFaultTolerance() throws ManagementClientRelatedException {
        try (OnlineManagementClient client = ManagementClientProvider.onlineStandalone()) {
            disableFaultTolerance(client);
        } catch (Exception ex) {
            throw new ManagementClientRelatedException(
                    "Disabling Fault Tolerance subsystem failed. This might crash other tests.", ex);
        }
    }

    /**
     * Disable Fault Tolerance subsystem and extension
     *
     * @param client {@link OnlineManagementClient} instance used to execute the command
     * @throws ManagementClientRelatedException Wraps exceptions thrown by the internal operation executed by
     *         {@link OnlineManagementClient} API
     */
    public static void disableFaultTolerance(OnlineManagementClient client) throws ManagementClientRelatedException {
        if (faultToleranceSubsystemExists(client)) {
            ManagementClientHelper.executeCliCommand(client, MICROPROFILE_FAULT_TOLERANCE_SUBSYSTEM_ADDRESS + ":remove()");
        }
        if (faultToleranceExtensionExists(client)) {
            ManagementClientHelper.executeCliCommand(client, MICROPROFILE_FAULT_TOLERANCE_EXTENSION_ADDRESS + ":remove()");
        }
        try {
            new Administration(client).reloadIfRequired();
        } catch (Exception ex) {
            throw new ManagementClientRelatedException(
                    "Disabling Fault Tolerance subsystem failed. This might crash other tests.", ex);
        }

    }

    /**
     * Checks whether <b>"org.wildfly.extension.microprofile.fault-tolerance-smallrye"</b> extension is present
     *
     * @return True if extension is already present,false otherwise
     * @throws ManagementClientRelatedException Wraps exceptions thrown by the internal operation executed by
     *                                          {@link OnlineManagementClient} API
     */
    public static Boolean faultToleranceExtensionExists(OnlineManagementClient client) throws ManagementClientRelatedException {
        Operations ops = new Operations(client);
        try {
            return ops.exists(MICROPROFILE_FAULT_TOLERANCE_EXTENSION_ADDRESS);
        } catch (IOException | OperationException e) {
            throw new ManagementClientRelatedException(e);
        }
    }

    /**
     * Checks whether <b>"microprofile-fault-tolerance-smallrye"</b> subsystem is present
     *
     * @return True if subsystem is already present,false otherwise
     * @throws ManagementClientRelatedException Wraps exceptions thrown by the internal operation executed by
     *                                          {@link OnlineManagementClient} API
     */
    public static Boolean faultToleranceSubsystemExists(OnlineManagementClient client) throws ManagementClientRelatedException {
        Operations ops = new Operations(client);
        try {
            return ops.exists(MICROPROFILE_FAULT_TOLERANCE_SUBSYSTEM_ADDRESS);
        } catch (IOException | OperationException e) {
            throw new ManagementClientRelatedException(e);
        }
    }
}
