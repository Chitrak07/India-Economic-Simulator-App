package com.india.econsim;

import com.india.econsim.controller.SimulatorServlet;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector();

        // Works both locally (src/main/webapp) and from packaged jar (target/webapp)
        String webappDir = new File("src/main/webapp").exists()
                ? new File("src/main/webapp").getAbsolutePath()
                : new File("target/webapp").getAbsolutePath();

        Context ctx = tomcat.addWebapp("", webappDir);
        Tomcat.addServlet(ctx, "SimulatorServlet", new SimulatorServlet());
        ctx.addServletMappingDecoded("/simulate", "SimulatorServlet");

        tomcat.start();
        System.out.println("Server started on port " + port);
        tomcat.getServer().await();
    }
}
