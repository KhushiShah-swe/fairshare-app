package com.fairshare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Apply CORS settings to all endpoints in the application
        registry.addMapping("/**")
                // Allow the specific origin of your React app
                .allowedOrigins("http://localhost:5173") 
                // Explicitly allow common HTTP methods
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // Allow all headers to prevent "Pre-flight" request failures
                .allowedHeaders("*")
                // CRITICAL: Must be true to send/receive HttpSession cookies
                .allowCredentials(true)
                // How long the browser should cache this CORS configuration (1 hour)
                .maxAge(3600);
    }
}