package org.jboss.eap.qe.microprofile.tooling.cpu.load.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper builder class for starting Java process.
 */
public class JavaProcessBuilder {

    private String mainClass;
    private String workingDirectory;
    private Map<String, String> systemProperties = new HashMap<String, String>();
    private List<String> classpathEntries = new ArrayList<String>();
    private List<String> mainClassArguments = new ArrayList<String>();
    private String javaRuntime = "java";

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    private String getClasspath() {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        final int totalSize = classpathEntries.size();
        for (String classpathEntry : classpathEntries) {
            builder.append(classpathEntry);
            count++;
            if (count < totalSize) {
                builder.append(System.getProperty("path.separator"));
            }
        }
        return builder.toString();
    }

    /**
     * Sets working directory for starting process.
     *
     * @param workingDirectory path to working directory
     */
    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Adds classes to classpath.
     *
     * @param classpathEntry class to add to classpath
     */
    public void addClasspathEntry(String classpathEntry) {
        this.classpathEntries.add(classpathEntry);
    }

    /**
     * Adds argument to java command
     *
     * @param argument argument to add
     */
    public void addArgument(String argument) {
        this.mainClassArguments.add(argument);
    }

    /**
     * Starts java process
     *
     * @return started java process
     */
    public Process startProcess() throws IOException {
        List<String> argumentsList = new ArrayList<String>();
        argumentsList.add(this.javaRuntime);

        for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append("-D").append(entry.getKey()).append("=").append(entry.getValue());
            argumentsList.add(sb.toString());
        }

        argumentsList.add("-classpath");
        argumentsList.add(getClasspath());
        argumentsList.add(this.mainClass);
        for (String arg : mainClassArguments) {
            argumentsList.add(arg);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(argumentsList.toArray(new String[argumentsList.size()]));
        StringBuilder str = new StringBuilder("Command:");
        for (String s : processBuilder.command()) {
            str.append(s).append(" ");
        }
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(new File(this.workingDirectory));
        return processBuilder.start();
    }
}
