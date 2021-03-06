package org.unizin.cmp.oai.harvester.service;

import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import org.apache.http.client.HttpClient;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.slf4j.MDC;
import org.unizin.cmp.oai.harvester.HarvestNotification;
import org.unizin.cmp.oai.harvester.HarvestNotification.HarvestNotificationType;
import org.unizin.cmp.oai.harvester.HarvestParams;
import org.unizin.cmp.oai.harvester.Harvester;
import org.unizin.cmp.oai.harvester.job.HarvestJob;
import org.unizin.cmp.oai.harvester.job.JobHarvestSpec;
import org.unizin.cmp.oai.harvester.job.JobNotification;
import org.unizin.cmp.oai.harvester.job.JobNotification.JobNotificationType;
import org.unizin.cmp.oai.harvester.service.config.HarvestJobConfiguration;
import org.unizin.cmp.oai.harvester.service.db.DBIUtils;
import org.unizin.cmp.oai.harvester.service.db.H2Functions.HarvestInfo;
import org.unizin.cmp.oai.harvester.service.db.H2Functions.JobInfo;

/**
 * Responsible for creating and providing status on all running jobs in the
 * system.
 * <p>
 * Instances are safe for use in multiple threads.
 * </p>
 */
public final class JobManager {

    public static final class JobCreationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final List<String> invalidBaseURIs;
        public JobCreationException(final List<String> invalidBaseURIs) {
            this.invalidBaseURIs = invalidBaseURIs;
        }

        public List<String> getInvalidBaseURIs() {
            return invalidBaseURIs;
        }
    }

    public static final String REPOSITORY_NAME = "repositoryName";
    public static final String REPOSITORY_INSTITUTION = "repositoryInstitution";
    public static final String HARVEST_NAME = "harvestName";
    public static final String JOB_NAME = "jobName";


    private final HarvestJobConfiguration jobConfig;
    private final HttpClient httpClient;
    private final DynamoDBClient dynamoClient;
    private final DBI dbi;
    private final Consumer<HarvestNotification> harvestFailureListener;
    private final ConcurrentMap<String, JobStatus> jobStatus =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, HarvestJob> jobs =
            new ConcurrentHashMap<>();


    public JobManager(final HarvestJobConfiguration jobConfig,
            final HttpClient httpClient, final DynamoDBClient dynamoClient,
            final DBI dbi,
            final Consumer<HarvestNotification> harvestFailureListener) {
        this.jobConfig = jobConfig;
        this.httpClient = httpClient;
        this.dynamoClient = dynamoClient;
        this.dbi = dbi;
        this.harvestFailureListener = harvestFailureListener;
    }

    private JobInfo addJobToDatabase(final List<HarvestParams> harvests) {
        try (final Handle handle = DBIUtils.handle(dbi)) {
            return handle.createCall("#info = call CREATE_JOB(#paramList)")
                    .bind("paramList", harvests)
                    .registerOutParameter("info", Types.OTHER)
                    .invoke().getObject("info", JobInfo.class);
        }
    }

    private List<JobHarvestSpec> buildSpecs(final String jobName,
            final JobInfo jobInfo, final List<HarvestParams> params) {
        final List<JobHarvestSpec> specs = new ArrayList<>();
        final Iterator<HarvestInfo> harvests = jobInfo.getHarvests().iterator();
        params.forEach(x -> {
            final Map<String, String> tags = new HashMap<>(1);
            final HarvestInfo harvestInfo = harvests.next();
            tags.put(HARVEST_NAME, harvestInfo.getName());
            tags.put(REPOSITORY_INSTITUTION,
                    harvestInfo.getRepositoryInstitution());
            tags.put(REPOSITORY_NAME, harvestInfo.getRepositoryName());
            tags.put(JOB_NAME, jobName);
            specs.add(new JobHarvestSpec(x, tags));
        });
        return specs;
    }

    private void harvestUpdate(final String jobName, final Object o,
            final Object arg) {
        if (o instanceof Harvester && arg instanceof HarvestNotification) {
            final JobStatus status = jobStatus.get(jobName);
            final HarvestNotification hn = (HarvestNotification)arg;
            if (hn.getType() == HarvestNotificationType.HARVEST_ENDED &&
                    hn.hasError()) {
                harvestFailureListener.accept(hn);
            }
            status.harvestUpdate(hn);
            jobStatus.put(jobName, status);
        }
    }

    private void jobUpdate(final String jobName, final Object o,
            final Object arg) {
        if (o instanceof HarvestJob && arg instanceof JobNotification) {
            final JobStatus status = jobStatus.get(jobName);
            final JobNotification notification = (JobNotification)arg;
            status.jobUpdate(notification);
            if (notification.getType() == JobNotificationType.STOPPED) {
                jobStatus.remove(jobName);
            } else {
                jobStatus.put(jobName, status);
            }
        }
    }

    /**
     * Create a new harvest job.
     *
     * @param executor
     *            the executor service that will manage the job's threads.
     * @param params
     *            parameters of harvests to include in the job.
     * @return the name of the newly-created job.
     *
     * @throws NoSuchAlgorithmException
     *             if the JDK in use does not support the standard MD5 hashing
     *             algorithm (very unlikely).
     * @throws java.util.concurrent.RejectedExecutionException
     *             if the executor cannot run the new job for some reason.
     * @throws JobCreationException
     *             if any of the harvests specify an invalid repository base
     *             URI.
     */
    public String newJob(final ExecutorService executor,
            final List<HarvestParams> params)
                    throws NoSuchAlgorithmException {
        final JobInfo jobInfo = addJobToDatabase(params);
        final List<String> invalidURIs = jobInfo.getInvalidRepositoryBaseURIs();
        if (! invalidURIs.isEmpty()) {
            throw new JobCreationException(invalidURIs);
        }
        final String jobName = String.valueOf(jobInfo.getID());
        final List<JobHarvestSpec> specs = buildSpecs(jobName, jobInfo, params);
        final Observer observeHarvests = (o, arg) -> {
            harvestUpdate(jobName, o, arg);
        };
        final HarvestJob job = jobConfig.job(httpClient,
                dynamoClient.getMapper(), executor,
                jobName, specs, Collections.singletonList(observeHarvests));
        job.addObserver((o, arg) -> jobUpdate(jobName, o, arg));
        jobStatus.put(jobName, new JobStatus(dbi));
        jobs.put(jobName, job);
        executor.submit(() -> {
            MDC.put(JOB_NAME, jobName);
            job.start();
        });
        return jobName;
    }

    public HarvestJob getJob(final String jobName) {
        return jobs.get(jobName);
    }

    public SortedMap<String, JobStatus> getRunningStatus() {
        return new TreeMap<>(jobStatus);
    }

    public JobStatus getStatus(final String jobName) {
        return jobStatus.get(jobName);
    }

    public long getMaxQueueSize() {
        final Optional<Long> l = jobStatus.values().stream()
                .map(JobStatus::getQueueSize)
                .max((x,y) -> Long.compare(x, y));
        if (l.isPresent()) {
            return l.get();
        }
        return 0;
    }
}
