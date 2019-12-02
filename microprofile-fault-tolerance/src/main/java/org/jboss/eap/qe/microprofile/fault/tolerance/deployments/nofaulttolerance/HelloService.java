package org.jboss.eap.qe.microprofile.fault.tolerance.deployments.nofaulttolerance;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HelloService {
    public String ping() {
        return "Pong from HelloService";
    }
}
