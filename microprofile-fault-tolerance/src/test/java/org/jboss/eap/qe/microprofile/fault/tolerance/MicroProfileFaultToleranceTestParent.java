package org.jboss.eap.qe.microprofile.fault.tolerance;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.core.ManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineOptions;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

import static org.junit.Assert.fail;

@RunWith(Arquillian.class)
public class MicroProfileFaultToleranceTestParent {

    private static final String NAME_OF_MICROPROFILE_FAULT_TOLERANCE_EXTENSION = "org.wildfly.extension.microprofile.fault-tolerance-smallrye";
    private static final String NAME_OF_MICROPROFILE_FAULT_TOLERANCE_SUBSYSTEM = "microprofile-fault-tolerance-smallrye";
    private static final String MANAGEMENT_ADDRESS = "127.0.0.1";
    private static final int MANAGEMENT_PORT = 9990;

    @BeforeClass
    public static void enableFaultToleracne() throws Exception {

        disableFaultToleracne(true); // safety check

        try (OnlineManagementClient client = getOnlineManagementClient()) {
            client.executeCli("/extension=" + NAME_OF_MICROPROFILE_FAULT_TOLERANCE_EXTENSION + ":add()");
            client.executeCli("/subsystem=" + NAME_OF_MICROPROFILE_FAULT_TOLERANCE_SUBSYSTEM + ":add()");
            Administration admin = new Administration(client);
            admin.reload();
        } catch (Exception ex)  {
            ex.printStackTrace();
        }
    }


    @AfterClass
    public static void disableFaultToleracne() throws Exception {
        disableFaultToleracne(false);
    }

    public static void disableFaultToleracne(boolean ignoreFailure) throws Exception {
        try (OnlineManagementClient client = getOnlineManagementClient()) {
            try {
                client.executeCli("/subsystem=" + NAME_OF_MICROPROFILE_FAULT_TOLERANCE_SUBSYSTEM + ":remove()");
                client.executeCli("/extension=" + NAME_OF_MICROPROFILE_FAULT_TOLERANCE_EXTENSION + ":remove()");
                Administration admin = new Administration(client);
                admin.reload();
            } catch (Exception ex)  {
                if (!ignoreFailure)  {
                    throw new Exception("Disabling Fault Tolerance subsystem failed. This might crash other tests.", ex);
                }
            }
        }
    }

    public static OnlineManagementClient getOnlineManagementClient() throws Exception {
        return ManagementClient.online(OnlineOptions
                .standalone()
                .hostAndPort(MANAGEMENT_ADDRESS, MANAGEMENT_PORT)
                .build()
        );
    }
}
