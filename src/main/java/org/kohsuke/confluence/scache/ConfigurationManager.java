package org.kohsuke.confluence.scache;

import com.atlassian.config.ApplicationConfiguration;
import com.atlassian.config.ConfigurationException;
import org.apache.log4j.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConfigurationManager {
    private final ApplicationConfiguration applicationConfig;

    public ConfigurationManager(ApplicationConfiguration applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    public boolean isConfigured() {
        return getRootPath()!=null;
    }

    public String getRootPath() {
        return getProperty("rootPath");
    }

    public void setRootPath(String v) {
        setProperty("rootPath",v);
    }

    public String getUserName() {
        return getProperty("userName");
    }

    public void setUserName(String v) {
        setProperty("userName",v);
    }

    public String getPassword() {
        return getProperty("password");
    }

    public void setPassword(String v) {
        setProperty("password",v);
    }

    public String getRetrievalUrl() {
        return getProperty("retrievalUrl");
    }

    public void setRetrievalUrl(String v) {
        setProperty("retrievalUrl",v);
    }



    private String getProperty(String name) {
        return (String)applicationConfig.getProperty(name);
    }

    private void setProperty(String name, String value) {
        applicationConfig.setProperty(name,value);
    }

    public void save() throws ConfigurationException {
        applicationConfig.save();
    }

    private static final Logger LOGGER = Logger.getLogger(ConfigurationManager.class);
}
