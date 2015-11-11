package org.unizin.cmp.oai.harvester.response;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.unizin.cmp.oai.harvester.Tests.defaultTestParams;

import javax.xml.stream.XMLStreamException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.unizin.cmp.oai.harvester.Harvester;
import org.unizin.cmp.oai.harvester.Tests;
import org.unizin.cmp.oai.harvester.exception.HarvesterException;
import org.unizin.cmp.oai.harvester.exception.HarvesterXMLParsingException;
import org.unizin.cmp.oai.mocks.Mocks;

import com.github.tomakehurst.wiremock.junit.WireMockRule;



public final class TestResponseHandlerErrorHandling {
    private static final String VALID_XML = "<someXML/>";

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Rule
    public final WireMockRule wireMock = Tests.newWireMockRule();

    @Before
    public void setupWiremock() {
        Tests.createWiremockStubForOKGetResponse(VALID_XML);
    }

    /**
     * Tests that an {@link XMLStreamException} thrown by the response handler
     * is correctly wrapped in a plain {@link HarvestException}, <em>not</em> a
     * {@link HarvesterXMLParsingException}.
     */
    @Test
    public void testResponseHandlerXMLStreamException() throws Exception {
        final Harvester harvester = new Harvester.Builder().build();
        final OAIResponseHandler mockHandler = Mocks.newResponseHandler();
        final OAIEventHandler mockEventHandler = mockHandler.getEventHandler(null);
        doThrow(new XMLStreamException(Mocks.TEST_EXCEPTION_MESSAGE))
            .when(mockEventHandler).onEvent(any());
        exception.expect(HarvesterException.class);
        try {
            harvester.start(defaultTestParams(), mockHandler);
        } catch (final HarvesterXMLParsingException e) {
            Assert.fail("No parsing exception expected here.");
        } catch (final HarvesterException e) {
            Mocks.assertTestException(e.getCause(), XMLStreamException.class);
            throw e;
        }
    }


    /**
     * Tests that {@code HarvesterXMLParsingExceptions} thrown by the response
     * handler are propagated to the caller.
     */
    @Test
    public void testResponseHandlerHarvestXMLParsingException() throws Exception {
        final Harvester harvester = new Harvester.Builder().build();
        final OAIResponseHandler mockHandler = Mocks.newResponseHandler();
        final OAIEventHandler mockEventHandler = mockHandler.getEventHandler(null);
        doThrow(new HarvesterXMLParsingException(Mocks.TEST_EXCEPTION_MESSAGE))
            .when(mockEventHandler).onEvent(any());
        exception.expect(HarvesterXMLParsingException.class);
        exception.expectMessage(Mocks.TEST_EXCEPTION_MESSAGE);
        harvester.start(defaultTestParams(), mockHandler);
    }
}