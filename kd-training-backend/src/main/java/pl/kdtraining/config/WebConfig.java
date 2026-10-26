package pl.kdtraining.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;
    private final TrainingConsentInterceptor trainingConsentInterceptor;

    public WebConfig(CurrentUserIdArgumentResolver currentUserIdArgumentResolver,
                     TrainingConsentInterceptor trainingConsentInterceptor) {
        this.currentUserIdArgumentResolver = currentUserIdArgumentResolver;
        this.trainingConsentInterceptor = trainingConsentInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(trainingConsentInterceptor)
                .addPathPatterns(TrainingConsentInterceptor.PATH_PATTERN)
                .excludePathPatterns(TrainingConsentInterceptor.EXCLUDED_PATHS);
    }
}
