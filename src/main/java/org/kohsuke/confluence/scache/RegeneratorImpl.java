package org.kohsuke.confluence.scache;

import com.atlassian.quartz.jobs.AbstractJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * @author Kohsuke Kawaguchi
 */
public class RegeneratorImpl extends AbstractJob {
    public RegeneratorImpl() {
        System.out.println("BRAVO");
    }

    @Override
    public void doExecute(JobExecutionContext context) throws JobExecutionException {
        ((RegeneratorJobDetail)context.getJobDetail()).getGenerator().regenerateAll();
    }
}
