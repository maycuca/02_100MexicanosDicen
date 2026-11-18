package mx.cienmexicanos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;
    private final PresenterTokenInterceptor presenterTokenInterceptor;

    public WebConfig(AppProperties properties, PresenterTokenInterceptor presenterTokenInterceptor) {
        this.properties = properties;
        this.presenterTokenInterceptor = presenterTokenInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE") // PUT: renameTeam (spec 003); DELETE: closeRoom (spec 005)
                .allowedHeaders("*");
    }

    /** T203: X-Presenter-Token en las acciones del presentador (AC-26). */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(presenterTokenInterceptor).addPathPatterns(PresenterTokenInterceptor.PATHS);
    }
}
