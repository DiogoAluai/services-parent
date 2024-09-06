package daluai.lib.services_parent;


import daluai.lib.encryption.AESEncrypter;
import daluai.lib.encryption.HashUtils;
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
 * The difference with {@link ApiKeyFilter} class, is that this one expects encrypted api key and checks against a hash.
 */
public abstract class EncryptedApiKeyFilter extends HttpFilter {

    private static final Logger LOG = LoggerFactory.getLogger(EncryptedApiKeyFilter.class);

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String requestEncryptedApiKey = request.getHeader(ApiKeyInterceptor.HEADER_AUTHORIZATION);
        if (requestEncryptedApiKey == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            LOG.warn("Did not find authorization header: " + request);
            return;
        }
        String requestApiKey = tryDecryptingOrFailMiserably(requestEncryptedApiKey);
        String hashedRequestApiKey = HashUtils.hash(requestApiKey, "SHA-512");
        if (hashedRequestApiKey.equals(getHashedApiKey())) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            LOG.warn("Received request using wrong api key: " + request);
        }
    }

    private String tryDecryptingOrFailMiserably(String requestEncryptedApiKey) {
        var decrypter = new AESEncrypter(getAESSecret());
        try {
            return decrypter.decrypt(requestEncryptedApiKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed miserably", e);
        }
    }

    /**
     * Get aes secret
     */
    protected abstract String getAESSecret();

    /**
     * Get sha512 api key digest.
     */
    protected abstract String getHashedApiKey();

}

