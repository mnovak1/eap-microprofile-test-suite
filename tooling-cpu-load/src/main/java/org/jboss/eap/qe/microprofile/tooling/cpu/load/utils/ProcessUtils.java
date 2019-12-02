package org.jboss.eap.qe.microprofile.tooling.cpu.load.utils;

import java.io.IOException;
import java.lang.reflect.Field;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.wildfly.extras.creaper.core.online.ModelNodeResult;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;

/**
 * Utility class for working with process.
 */
public class ProcessUtils {

    /**
     * Retrieves process id of EAP/Wildfly server using managament client
     *
     * @param client management client connected to server
     * @return pid of the server
     */
    public static int getProcessId(OnlineManagementClient client) throws IOException {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("read-resource");
        model.get(ClientConstants.OP_ADDR).add("core-service", "platform-mbean");
        model.get(ClientConstants.OP_ADDR).add("type", "runtime");

        ModelNodeResult result = client.execute(model);
        String nodeName = result.get("result").get("name").asString();
        return Integer.valueOf(nodeName.substring(0, nodeName.indexOf("@")));
    }

    /**
     * Returns pid of given process.
     *
     * @param process process
     * @return pid of the process
     */
    public static int getProcessId(Process process) {

        int pid;
        try {
            Field f = process.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            pid = f.getInt(process);
        } catch (Throwable e) {
            throw new IllegalStateException("Unable to get process id for " + process.getClass().getSimpleName(), e);
        }

        if (pid == 0) {
            throw new IllegalStateException("Process ID is 0 for " + process.getClass().getSimpleName());
        }

        return pid;
    }

    /**
     * Priority can be set -19 to 20, lower number = higher priority
     *
     * @param pid id of the process
     * @param priority priority to be set, -19 to 20, lower number = higher priority
     */
    public static void setPriorityToProcess(String pid, int priority) throws Exception {

        if (isPriorityInRange(priority)) {

            if (!System.getProperty("os.name").contains("Linux")) {
                throw new UnsupportedOperationException(
                        "Command renice which is used to lower priority of process is supported only on linux. " +
                                "Current operation system is: " + System.getProperty("os.name").contains("Linux"));
            }

            Process setPriorityProcess = Runtime.getRuntime().exec("renice -n " + priority + " -p " + pid);
            if (setPriorityProcess.waitFor() != 0) {
                throw new Exception("Process: " + setPriorityProcess + " did not exit with code 0.");
            }
        } else {
            throw new Exception("Set process priority between -19 to 20. Priority was set to: " + priority);
        }
    }

    private static boolean isPriorityInRange(int priority) {
        if (priority < -19 || priority > 20) {
            return false;
        }
        return true;
    }

    /**
     * Binds process with process to given CPU cores.
     *
     * @param pid process id
     * @param cpuIds Specify cores process to bind on - for example "0-2,6" will bind process to 4 CPU cores (0,1,2,6)
     * @throws Exception throws exception if this operation fails
     */
    public static void bindProcessToCPU(int pid, String cpuIds) throws Exception {
        String cmd;
        if (System.getProperty("os.name").contains("Linux")
                && (System.getProperty("os.version").contains("el7") || System.getProperty("os.version").contains("fc2"))) {
            cmd = "taskset -a -cp " + cpuIds + " " + pid;
        } else {
            throw new RuntimeException(
                    "Command for binding process to CPU core is not implemented for this OS. Check BindProcessToCpuUtils.bindProcessToCPU() and implement for your OS.");
        }

        if (Runtime.getRuntime().exec(cmd).waitFor() != 0) {
            throw new Exception("Command: " + cmd + " failed.");
        }
    }
}
