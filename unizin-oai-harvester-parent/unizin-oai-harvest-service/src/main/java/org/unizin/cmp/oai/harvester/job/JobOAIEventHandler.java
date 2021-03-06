package org.unizin.cmp.oai.harvester.job;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unizin.cmp.oai.OAIXMLUtils;
import org.unizin.cmp.oai.harvester.exception.HarvesterException;
import org.unizin.cmp.oai.harvester.response.RecordOAIEventHandler;

/**
 * Record event handler that constructs {@link HarvestedOAIRecord} instances.
 *
 */
public final class JobOAIEventHandler
extends RecordOAIEventHandler<HarvestedOAIRecord> {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            JobOAIEventHandler.class);

    private final String baseURL;
    private final XMLOutputFactory outputFactory;
    private final MessageDigest messageDigest;

    static XMLOutputFactory defaultOutputFactory() {
        final XMLOutputFactory out = OAIXMLUtils.newOutputFactory();
        out.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
        return out;
    }

    public JobOAIEventHandler(final URI baseURI,
            final Consumer<HarvestedOAIRecord> recordConsumer)
                    throws NoSuchAlgorithmException {
        this(baseURI, recordConsumer, defaultOutputFactory(),
                HarvestJob.digest());
    }

    public JobOAIEventHandler(final URI baseURI,
            final Consumer<HarvestedOAIRecord> recordConsumer,
            final XMLOutputFactory outputFactory,
            final MessageDigest messageDigest) {
        super(recordConsumer);
        this.baseURL = baseURI.toString();
        this.outputFactory = outputFactory;
        final Object o = outputFactory.getProperty(
                XMLOutputFactory.IS_REPAIRING_NAMESPACES);
        if (! (o instanceof Boolean && (boolean)o)) {
            throw new IllegalArgumentException(
                    "Output factory must repair namespaces.");
        }
        this.messageDigest = messageDigest;
    }

    private byte[] checksum(final byte[] bytes) {
        messageDigest.update(bytes);
        return messageDigest.digest();
    }

    private byte[] createXML(final List<XMLEvent> events)
            throws XMLStreamException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final XMLEventWriter writer = OAIXMLUtils.createEventWriter(
                outputFactory, baos);
        for (final XMLEvent event : events) {
            writer.add(event);
        }
        writer.flush();
        writer.close();
        final byte[] bytes = baos.toByteArray();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(new String(bytes, StandardCharsets.UTF_8));
        }
        return bytes;
    }

    private byte[] compress(final byte[] rawBytes) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final GZIPOutputStream out = new GZIPOutputStream(baos)) {
            out.write(rawBytes);
        } catch (final IOException e) {
            throw new HarvesterException(e);
        }
        return baos.toByteArray();
    }

    @Override
    protected void onDatestamp(final HarvestedOAIRecord currentRecord,
            final String datestamp) {
        currentRecord.setDatestamp(datestamp);
    }

    @Override
    protected void onIdentifier(final HarvestedOAIRecord currentRecord,
            final String identifier) {
        currentRecord.setIdentifier(identifier);
    }

    @Override
    protected void onSet(final HarvestedOAIRecord currentRecord,
            final String set) {
        currentRecord.addSet(set);
    }

    @Override
    protected void onStatus(final HarvestedOAIRecord currentRecord,
            final String status) {
        currentRecord.setStatus(status);
    }

    @Override
    protected void onRecordEnd(final HarvestedOAIRecord currentRecord,
            final List<XMLEvent> recordEvents) {
        try {
            final byte[] bytes = createXML(recordEvents);
            final byte[] checksum = checksum(bytes);
            currentRecord.setXml(compress(bytes));
            currentRecord.setChecksum(checksum);
        } catch (final XMLStreamException e) {
            throw new HarvesterException(e);
        }
    }

    @Override
    protected HarvestedOAIRecord createRecord(
            final StartElement recordStartElement) {
        final HarvestedOAIRecord record = new HarvestedOAIRecord();
        record.setBaseURL(baseURL);
        return record;
    }
}
