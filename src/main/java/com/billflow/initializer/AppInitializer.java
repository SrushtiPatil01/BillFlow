package com.billflow.initializer;

import com.billflow.config.AppConfig;
import com.billflow.config.WebConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Replaces web.xml. Programmatically registers the DispatcherServlet
 * with Spring's application context.
 */
public class AppInitializer implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext servletContext) {
        AnnotationConfigWebApplicationContext context =
                new AnnotationConfigWebApplicationContext();
        context.register(AppConfig.class, WebConfig.class);

        DispatcherServlet dispatcher = new DispatcherServlet(context);

        ServletRegistration.Dynamic registration =
                servletContext.addServlet("dispatcher", dispatcher);
        registration.setLoadOnStartup(1);
        registration.addMapping("/api/*");
    }
}