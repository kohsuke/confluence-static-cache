package org.kohsuke.confluence.scache;

import org.quartz.JobDetail;

/**
 * See https://developer.atlassian.com/display/CONFDEV/Workaround+pattern+for+autowiring+jobs
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
