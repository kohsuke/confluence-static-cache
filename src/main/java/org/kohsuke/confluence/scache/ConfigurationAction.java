package org.kohsuke.confluence.scache;

import com.atlassian.config.ConfigurationException;
import com.atlassian.confluence.core.Administrative;
import com.atlassian.confluence.core.ConfluenceActionSupport;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConfigurationAction extends ConfluenceActionSupport implements Administrative {
    private ConfigurationManager configurationManager;
    private StaticPageGenerator staticPageGenerator;

    private String rootPath;
    private String userName;
    private String password;
    private String retrievalUrl;

    public void setConfigurationManager(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    public void setStaticPageGenerator(StaticPageGenerator staticPageGenerator) {
        this.staticPageGenerator = staticPageGenerator;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRetrievalUrl() {
        return retrievalUrl;
    }

    public void setRetrievalUrl(String retrievalUrl) {
        this.retrievalUrl = retrievalUrl;
    }

    /**
     * Display the current configuration.
     */
    public String execute() {
        rootPath = configurationManager.getRootPath();
        userName = configurationManager.getUserName();
        password = configurationManager.getPassword();
        retrievalUrl = configurationManager.getRetrievalUrl();
        return SUCCESS;
    }

    public String configure() {
        configurationManager.setRootPath(rootPath);
        configurationManager.setUserName(userName);
        configurationManager.setPassword(password);
        configurationManager.setRetrievalUrl(retrievalUrl);

        try {
            configurationManager.save();
            addActionMessage("Saved");
        } catch (ConfigurationException e) {
            addActionError("Failed to save: " + e.getMessage());
        }

        return SUCCESS;
    }

    public String regenerate() {
        staticPageGenerator.regenerateAll();
        addActionMessage("Regeneration started");
        return SUCCESS;
    }
}
