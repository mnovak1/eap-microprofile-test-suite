package org.jboss.eap.qe.microprofile.tooling.cpu.load;

import java.io.File;
import java.time.Duration;

import org.jboss.eap.qe.microprofile.tooling.cpu.load.utils.CpuLoadGenerator;
import org.jboss.eap.qe.microprofile.tooling.cpu.load.utils.JavaProcessBuilder;
import org.jboss.eap.qe.microprofile.tooling.cpu.load.utils.ProcessUtils;

/**
 * Utility class causing 100% CPU load on given CPU cores.
 * <p>
 * NOTE THIS UTILITY CLASS IS DEPENDENT ON RHEL/FEDORA SYSTEMS.
 */
public class HighCPUUtils {

    /**
     * Simulates that EAP/Wildfly server will be under 100% CPU load.
     * <p>
     * This method binds process with WF/EAP to given CPU cores and then causes 100% load on those cores so WF/EAP
     * process has very little CPU time (almost not scheduled) on those cores.
     *
     * @param cpuIds specifies CPU cores to which EAP server and cpu load generator will be bound
     *        (for example "0-3", "0,1", ...)
     * @return return process of CPU loader tool, it can be destroyed later
     * @throws Exception if causing CPU load on given process fails
     */
    public static Process causeMaximumCPULoadOnContainer(int containerProcessId, String cpuIds, Duration durationOfLoad)
            throws Exception {

        if (canRunOnThisEnvironment()) {
            throw new RuntimeException("This tooling can be used only on RHEL or Fedora machines as it depends " +
                    "OS native commands like taskset and renice.");
        }
        // bind container process to given CPU cores
        ProcessUtils.bindProcessToCPU(containerProcessId, cpuIds);
        ProcessUtils.setPriorityToProcess(String.valueOf(containerProcessId), 19);

        // start CPU load generator and bind to the same CPU cores
        Process process = generateLoadInSeparateProcess(durationOfLoad);
        ProcessUtils.bindProcessToCPU(ProcessUtils.getProcessId(process), cpuIds);

        return process;
    }

    /**
     * Returns true if HighCPUUtils can run on this environment. This is important as this tooling is platform dependent.
     *
     * @return true if HighCPUUtils can run on this environment.
     */
    public static boolean canRunOnThisEnvironment() {
        return System.getProperty("os.name").contains("Linux") && (System.getProperty("os.version").contains("el")
                || System.getProperty("os.version").contains("fc2"));
    }

    private static Process generateLoadInSeparateProcess(Duration durationOfLoad) throws Exception {
        JavaProcessBuilder javaProcessBuilder = new JavaProcessBuilder();
        javaProcessBuilder.addClasspathEntry(System.getProperty("java.class.path"));
        javaProcessBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        javaProcessBuilder.addArgument(String.valueOf(durationOfLoad.toMillis()));
        javaProcessBuilder.setMainClass(CpuLoadGenerator.class.getName());
        Process process = javaProcessBuilder.startProcess();
        return process;
    }

}
