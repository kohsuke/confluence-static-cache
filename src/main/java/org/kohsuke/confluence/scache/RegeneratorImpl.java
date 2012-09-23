package org.kohsuke.confluence.scache;

import com.atlassian.quartz.jobs.AbstractJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * @author Kohsuke Kawaguchi
 */
public class RegeneratorImpl extends AbstractJob {
    private StaticPageGenerator generator;

    public RegeneratorImpl() {
        System.out.println("BRAVO");
    }

    @Override
    public void doExecute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        generator.regenerateAll();
    }

    public void setGenerator(StaticPageGenerator generator) {
        this.generator = generator;
    }
}
