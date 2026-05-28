package com.example.opcua;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class OpcUaClientApplication {

    private static final Logger log = LoggerFactory.getLogger(OpcUaClientApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(OpcUaClientApplication.class, args);
        Environment env = context.getEnvironment();

        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            String port = env.getProperty("server.port", "8081");
            String path = env.getProperty("server.servlet.context-path", "");

            log.info("\n----------------------------------------------------------\n" +
                    "\tApplication '{}' is running! Access URLs:\n" +
                    "\tLocal: \t\thttp://localhost:{}{}\n" +
                    "\tExternal: \thttp://{}:{}{}\n" +
                    "\tSwagger: \thttp://localhost:{}{}/swagger-ui.html\n" +
                    "\t----------------------------------------------------------",
                    env.getProperty("spring.application.name", "opcua-client"),
                    port, path,
                    host, port, path,
                    port, path);
        } catch (UnknownHostException e) {
            log.error("获取主机地址失败", e);
        }
    }

}