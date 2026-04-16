package com.nexilo;

import com.nexilo.common.config.EnvironmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

import java.lang.invoke.MethodHandles;

@SpringBootApplication
@EnableConfigurationProperties(EnvironmentProperties.class)
public class NexiloApplication {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(NexiloApplication.class);
        app.addListeners(new ApplicationPidFileWriter());
        Environment env = app.run(args).getEnvironment();
        logger.info("\n---------------------------\n\t" +
                "Application '{}' is running! Access URLs: Local: http://localhost:{}" +
                "\n---------------------------\n",
                env.getProperty("spring.application.name"),
                env.getProperty("server.port")
                );
    }
}
