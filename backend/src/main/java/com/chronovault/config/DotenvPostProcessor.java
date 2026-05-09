package com.chronovault.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

public class DotenvPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory(findProjectRoot())
                    .ignoreIfMissing()
                    .load();

            Properties props = new Properties();
            dotenv.entries().forEach(entry -> props.setProperty(entry.getKey(), entry.getValue()));

            if (!props.isEmpty()) {
                environment.getPropertySources().addFirst(new PropertiesPropertySource("dotenv", props));
            }
        } catch (Exception e) {
            // .env file not found or invalid - skip silently
        }
    }

    private String findProjectRoot() {
        // Try backend parent (project root) first, then current dir
        String userDir = System.getProperty("user.dir");
        if (userDir.endsWith("backend")) {
            return userDir + "/..";
        }
        return userDir;
    }
}
