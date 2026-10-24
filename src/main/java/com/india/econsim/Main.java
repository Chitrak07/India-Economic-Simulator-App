package com.india.econsim;

import com.india.econsim.controller.SimulatorServlet;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        tomcat.getConnector();

        String webappDir = new File("src/main/webapp").getAbsolutePath();
        Context ctx = tomcat.addWebapp("", webappDir);

        Tomcat.addServlet(ctx, "SimulatorServlet", new SimulatorServlet());
        ctx.addServletMappingDecoded("/simulate", "SimulatorServlet");

        tomcat.start();
        System.out.println("Server started at http://localhost:8080/simulator.jsp");
        tomcat.getServer().await();
    }
}
