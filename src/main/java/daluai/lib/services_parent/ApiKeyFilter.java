package daluai.lib.services_parent;


import daluai.lib.network_utils.ApiKeyInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;


/**
 * To add an Api key filter, extend this class and add {@link Component} annotation to it.
 * To add an Api key to http requests check {@link ApiKeyInterceptor}.
 */
public abstract class ApiKeyFilter extends HttpFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyFilter.class);

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String apiKey = request.getHeader(ApiKeyInterceptor.HEADER_AUTHORIZATION);

        if (getApiKey().equals(apiKey)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            LOG.warn("Received request using wrong api key: " + request);
        }
    }

    protected abstract String getApiKey();

}

