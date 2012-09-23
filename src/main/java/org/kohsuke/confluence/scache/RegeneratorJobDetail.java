package org.kohsuke.confluence.scache;

import org.quartz.JobDetail;

/**
 * Schedules periodic execution of the full regeneration.
 *
 * See https://developer.atlassian.com/display/CONFDEV/Workaround+pattern+for+autowiring+jobs
 * See https://confluence.atlassian.com/display/CONF29/Job+Plugins
 * See https://confluence.atlassian.com/display/CONF29/Trigger+Plugins
 *
 * @author Kohsuke Kawaguchi
 */
public class RegeneratorJobDetail extends JobDetail {
    private final StaticPageGenerator generator;

    public RegeneratorJobDetail(StaticPageGenerator generator) {
        this.generator = generator;
        setName(getClass().getName());
        setJobClass(RegeneratorImpl.class);
    }

    public StaticPageGenerator getGenerator() {
        return generator;
    }
}
