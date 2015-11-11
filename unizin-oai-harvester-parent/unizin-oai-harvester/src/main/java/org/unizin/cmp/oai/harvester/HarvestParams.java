package org.unizin.cmp.oai.harvester;

import java.net.URI;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.unizin.cmp.oai.OAI2Constants;
import org.unizin.cmp.oai.OAIDateGranularity;
import org.unizin.cmp.oai.OAIRequestParameter;
import org.unizin.cmp.oai.OAIVerb;
import org.unizin.cmp.oai.ResumptionToken;


/**
 * Convenience class for assembling and optionally validating OAI harvest
 * request parameters.
 * <p>
 * Instances are immutable once configured.
 */
public final class HarvestParams {
    private final URI baseURI;
    private final OAIVerb verb;
    private final Map<String, String> standardParameters = new HashMap<>();
    private final Map<String, String> nonstandardParameters = new HashMap<>();


    public HarvestParams(final URI baseURI, final OAIVerb verb) {
        Objects.requireNonNull(baseURI, "baseURI");
        this.baseURI = baseURI;
        this.verb = verb;
        // Ensure reasonable default metadataPrefix if it's required.
        if (verb.requiresParameter(OAIRequestParameter.METADATA_PREFIX)) {
            put(OAIRequestParameter.METADATA_PREFIX,
                    OAI2Constants.DEFAULT_METADATA_PREFIX);
        }
    }

    public URI getBaseURI() {
        return baseURI;
    }

    public OAIVerb getVerb() {
        return verb;
    }

    private void put(final OAIRequestParameter param, final String value) {
        standardParameters.put(param.paramName(), value);
    }

    public HarvestParams withFrom(final TemporalAccessor from,
            final OAIDateGranularity granularity) {
        return withFrom(granularity.format(from));
    }

    public HarvestParams withFrom(final String from) {
        put(OAIRequestParameter.FROM, from);
        return this;
    }

    public HarvestParams withIdentifier(final String identifier) {
        put(OAIRequestParameter.IDENTIFIER, identifier);
        return this;
    }

    public HarvestParams withMetadataPrefix(final String prefix) {
        put(OAIRequestParameter.METADATA_PREFIX, prefix);
        return this;
    }

    public HarvestParams withResumptionToken(final String resumptionToken) {
        put(OAIRequestParameter.RESUMPTION_TOKEN, resumptionToken);
        return this;
    }

    public HarvestParams withSet(final String setSpec) {
        put(OAIRequestParameter.SET, setSpec);
        return this;
    }

    public HarvestParams withUntil(final TemporalAccessor until,
            final OAIDateGranularity granularity) {
        return withUntil(granularity.format(until));
    }

    public HarvestParams withUntil(final String until) {
        put(OAIRequestParameter.UNTIL, until);
        return this;
    }

    /**
     * Add an arbitrary nonstandard parameter.
     * <p>
     * Nonstandard parameters are never validated.
     * <p>
     * Nonstandard parameters are added only to the initial request. To use
     * a nonstandard parameter in both the initial and subsequent requests,
     * add it to the {@code baseURI} instead.
     * @param name the parameter's name
     * @param value the parameter's value
     * @return this instance
     */
    public HarvestParams withNonstandardParameter(final String name,
            final String value) {
        nonstandardParameters.put(name, value);
        return this;
    }

    /**
     * Validate the standard harvest parameters.
     * @return {@code true} iff this instance's standard parameters are valid
     * with this instance's verb.
     */
    public boolean areValid() {
        return verb.areValidParameters(standardParameters);
    }

    public Map<String, String> getParameters() {
        final Map<String, String> allParams = new HashMap<>(standardParameters);
        allParams.putAll(nonstandardParameters);
        allParams.put(OAI2Constants.VERB_PARAM_NAME, verb.localPart());
        return allParams;
    }

    public HarvestParams getRetryParameters(final ResumptionToken resumptionToken) {
        final HarvestParams p = new HarvestParams(baseURI, verb);
        if (resumptionToken != null) {
            p.withResumptionToken(resumptionToken.getToken());
        } else {
            p.standardParameters.putAll(standardParameters);
            p.nonstandardParameters.putAll(nonstandardParameters);
        }
        return p;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((baseURI == null) ? 0 : baseURI.hashCode());
        result = prime * result + ((nonstandardParameters == null) ? 0 : nonstandardParameters.hashCode());
        result = prime * result + ((standardParameters == null) ? 0 : standardParameters.hashCode());
        result = prime * result + ((verb == null) ? 0 : verb.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        HarvestParams other = (HarvestParams) obj;
        if (baseURI == null) {
            if (other.baseURI != null)
                return false;
        } else if (!baseURI.equals(other.baseURI))
            return false;
        if (nonstandardParameters == null) {
            if (other.nonstandardParameters != null)
                return false;
        } else if (!nonstandardParameters.equals(other.nonstandardParameters))
            return false;
        if (standardParameters == null) {
            if (other.standardParameters != null)
                return false;
        } else if (!standardParameters.equals(other.standardParameters))
            return false;
        if (verb != other.verb)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return getParameters().toString();
    }
}