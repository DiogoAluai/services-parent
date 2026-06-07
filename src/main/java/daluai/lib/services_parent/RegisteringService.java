package daluai.lib.services_parent;


import daluai.lib.network_utils.LocalIpProbe;
import daluai.lib.registry_api.Service;
import daluai.lib.registry_client.RegistryClient;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static daluai.lib.registry_api.Coms.ENDPOINT_HEALTH_CHECK;
import static daluai.lib.registry_api.Coms.OK_HEALTH;

/**
 * Extend this class as a {@link org.springframework.web.bind.annotation.RestController RestController} to
 * automatically register to the registry, and keep an eye on private ip changes.
 */
public abstract class RegisteringService {

    private static final Logger LOG = LoggerFactory.getLogger(RegisteringService.class);

    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();

    private String url;

    public RegisteringService(String name) {
        validateChildClassAnnotations();
        String privateIp = LocalIpProbe.firstActiveIPv4Address();
        String port = Optional.ofNullable(System.getProperty("port.for.registry"))
                .orElseThrow(() -> new IllegalArgumentException("Property 'port.for.registry' must be provided"));
        this.url = buildUrl(privateIp, port);
        registerService(new Service(
                name,
                url,
                true
        ));
    }

    /**
     * Make sure child class is annotated with RestController and without RequestMapping.
     * <br>
     * RequestMapping with mess with health checking path assumptions.
     */
    private void validateChildClassAnnotations() {
        Class<?> childClass = getClass();
        List<String> errorMessages = new ArrayList<>();
        if (!childClass.isAnnotationPresent(RestController.class)) {
            errorMessages.add("Must be annotated with " + RestController.class.getSimpleName());
        }
        if (childClass.isAnnotationPresent(RequestMapping.class)) {
            errorMessages.add("Must not be annotated with " + RequestMapping.class.getSimpleName());
        }
        if (!errorMessages.isEmpty()) {
            throw new IllegalArgumentException("Class '" + childClass.getSimpleName() + "' miss-annotated: "
                    + String.join("; ", errorMessages));
        }
    }

    protected String buildUrl(String privateIp, String port) {
        return "http://" + privateIp + ":" + port;
    }

    /**
     * Send register command and set deregister hook for app shutdown
     */
    protected void registerService(Service service) {
        LOG.info("Registering service in registry: {}", service);
        RegistryClient.get().register(service);
        setUnregisterShutdownHook(service);
        EXECUTOR_SERVICE.scheduleAtFixedRate(() -> {
            // private ip may change, so let's check now and then and keep registry updated
            String newPrivateIp = LocalIpProbe.firstActiveIPv4Address();
            String port = Optional.ofNullable(System.getProperty("port.for.registry"))
                    .orElseThrow(() -> new IllegalArgumentException("Property 'port.for.registry' must be provided"));
            String newUrl = buildUrl(newPrivateIp, port);
            if (!url.equals(newUrl)) {
                LOG.info("IP address has changed from {} to {}", url, newUrl);
                url = newUrl;
                registerService(new Service(
                        service.name(),
                        newUrl,
                        true
                ));
            } else {
                LOG.info("Private ip unchanged: {}", url);
            }
        }, 1, 1, TimeUnit.HOURS);
    }
    /**
     * Set deregister hook to be called on shutdown.
     * You might want to override this for services with cold start, since they'll deregister when going dark.
     */
    protected void setUnregisterShutdownHook(Service service) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOG.info("Unregistering service in registry: {}", service);
                RegistryClient.get().deregister(service.name());
                LOG.info("Done unregistering");
            } catch (Exception e) {
                LOG.error("Failed unregistering service in registry: {}", service);
            }
        }));
    }

    @GetMapping(ENDPOINT_HEALTH_CHECK)
    public String healthCheck() {
        return OK_HEALTH;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")
                        .allowedMethods("*")
                        .allowedHeaders("*");
            }
        };
    }
}
