package pl.kdtraining.config;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import pl.kdtraining.infrastructure.security.JwtAuthenticatedUser;

import java.util.UUID;

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
            && parameter.getParameterType().equals(UUID.class);
    }

    /**
     * Answering "nobody is logged in" with {@code null} is the contract, not a failure: Spring
     * declares the return and both optional collaborators as nullable on the interface, so an
     * override that omits the marks narrows a contract the framework deliberately left wide.
     */
    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter,
                                            @Nullable ModelAndViewContainer mavContainer,
                                            NativeWebRequest webRequest,
                                            @Nullable WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtAuthenticatedUser jwtUser) {
            return jwtUser.getUserId();
        }

        if (auth != null && auth.getPrincipal() instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getUserId();
        }

        // No DEV_USER_ID session fallback: the filter chain is STATELESS, so there was never a
        // session to read it from, and an identity source that answers on production without any
        // profile guard is not something to keep around on the off chance.
        return null;
    }
}
