package org.unizin.cmp.oai.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.apache.http.HttpStatus;
import org.unizin.cmp.oai.templates.ErrorsTemplate;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import freemarker.template.TemplateException;

public final class WireMockUtils {

    public static final int DEFAULT_WIREMOCK_PORT = 9000;
    public static final int WIREMOCK_PORT = Integer.parseInt(System.getProperty(
            "wiremock.port", String.valueOf(DEFAULT_WIREMOCK_PORT)));
    public static final URI MOCK_OAI_BASE_URI;
    static {
        try {
            MOCK_OAI_BASE_URI = new URI(String.format("http://localhost:%d/oai",
                    WIREMOCK_PORT));
        } catch (final URISyntaxException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final String LITERAL_QM = Pattern.quote("?");
    public static final String URL_PATTERN_WITHOUT_RESUMPTION_TOKEN =
            "^.*" + LITERAL_QM + "(?:(?!resumptionToken).)*$";

    public static String urlResmptionTokenPattern(
            final String resumptionToken) {
        return "^.*" + LITERAL_QM + ".*resumptionToken=" +
                Pattern.quote(resumptionToken) + ".*$";
    }

    public static WireMockRule newWireMockRule() {
        return new WireMockRule(WIREMOCK_PORT);
    }

    public static void getStub(final String responseBody) {
        getStub(HttpStatus.SC_OK, responseBody);
    }

    public static void getStub(final int statusCode,
            final String responseBody) {
        getStub(statusCode, responseBody, ".*");
    }

    public static void getStub(final int statusCode, final String responseBody,
            final String urlPattern) {
        stubFor(get(urlMatching(urlPattern))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withBody(responseBody)));
    }

    public static String setupWithDefaultErrorResponse()
            throws TemplateException, IOException {
        final String defaultError = ErrorsTemplate.process();
        getStub(defaultError);
        return defaultError;
    }

    /** No instances allowed. */
    private WireMockUtils() { }
}