package org.jboss.eap.qe.microprofile.fault.tolerance.deployments.nofaulttolerance;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/")
public class HelloServlet extends HttpServlet {

    @Inject
    private HelloService hello;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String operation = req.getParameter("operation");
        switch (operation) {
            case "ping":
                resp.getWriter().print(hello.ping());
                break;
        }
    }
}
