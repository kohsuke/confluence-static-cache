package org.kohsuke.confluence.scache;

import org.quartz.JobDetail;

/**
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
