package org.kohsuke.confluence.scache;

import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.event.events.content.comment.CommentEvent;
import com.atlassian.confluence.event.events.content.page.PageEvent;
import com.atlassian.confluence.event.events.content.page.PageRemoveEvent;
import com.atlassian.confluence.event.events.label.LabelEvent;
import com.atlassian.confluence.labels.Labelable;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.event.Event;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
public class StaticPageGenerator {
    private final ScheduledExecutorService worker = new ScheduledThreadPoolExecutor(1,DAEMON_THREAD_FACTORY);

    private final ConfigurationManager configurationManager;
    private final SpaceManager spaceManager;
    private final PageManager pageManager;

    private final HttpClient client;

    public class Task {
        final String url;
        final File output;
        private final String key;

        public Task(Page page) {
            key = page.getSpaceKey()+'/'+page.getTitle();
            url = configurationManager.getRetrievalUrl()+page.getUrlPath();
            output = new File(configurationManager.getRootPath(),page.getSpaceKey()+'/'+page.getTitle()+".html");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Task task = (Task) o;

            return key.equals(task.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        public void execute() throws IOException, InterruptedException {
            LOGGER.info("Regenerating "+url);

            HttpMethod get = new GetMethod(url);
            String auth = configurationManager.getUserName()+':'+configurationManager.getPassword();
            get.setRequestHeader("Authorization", "Basic " + new String(Base64.encodeBase64(auth.getBytes())));

            int r = client.executeMethod(get);
            if (r /100==2) {
                // write to the output file atomically
                output.getParentFile().mkdirs();
                File tmp = new File(output.getPath()+".tmp");
                FileOutputStream fos = new FileOutputStream(tmp);
                try {
                    String html = IOUtils.toString(get.getResponseBodyAsStream(), "UTF-8");
                    html = transformHtml(html);
                    IOUtils.write(html, fos, "UTF-8");
                } finally {
                    IOUtils.closeQuietly(fos);
                    get.releaseConnection();
                }
                tmp.renameTo(output);
                LOGGER.info("Generated "+output);
            } else {
                LOGGER.warn("Request to "+url+" failed: "+r);
            }
        }

        public void delete() {
            output.delete();
        }
    }

    private String transformHtml(String s) {
        String userMenuLink = "id=\"user-menu-link\"";
        return s.replace(userMenuLink,userMenuLink+" style='display:none'");
    }

    public StaticPageGenerator(ConfigurationManager configurationManager, PageManager pageManager, SpaceManager spaceManager) throws IOException {
        this.spaceManager = spaceManager;
        this.pageManager = pageManager;
        this.configurationManager = configurationManager;

        client = new HttpClient();
//            client.getHostConfiguration().setHost("wiki2.jenkins-ci.org", 443,
//                    new Protocol("https", (ProtocolSocketFactory)new EasySSLProtocolSocketFactory(), 443));
        Protocol.registerProtocol("https", new Protocol("https", new EasySSLProtocolSocketFactory(), 443));

        worker.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                regenerateAll();
            }
        },6,6,TimeUnit.HOURS);
    }

    public void regenerateAll() {
        LOGGER.info("Rescheduling the generation of everything");
        for (Space space : spaceManager.getAllSpaces()) {
            if (space.isPersonal()) continue;   // don't care about personal space

            List<Page> pagesList = pageManager.getPages(space, true);
            for (Page page : pagesList) {
                submit(page,false);
            }
        }
    }

    private Properties loadConfigFile() throws IOException {
        Properties props = new Properties();
        File config = new File(new File(System.getProperty("user.home")), ".static-page-generator");
        if (config.exists()) {
            FileInputStream in = new FileInputStream(config);
            try {
                props.load(in);
            } finally {
                in.close();
            }
        }
        return props;
    }

    public void onEvent(Event event) {
        LOGGER.info("Handling " + event);
        if (event instanceof PageRemoveEvent) {
            new Task(((PageEvent) event).getPage()).delete();
        }
        if (event instanceof PageEvent) {
            submit(((PageEvent) event).getPage(),true);
        }
        if (event instanceof LabelEvent) {
            Labelable labelled = ((LabelEvent) event).getLabelled();
            if (labelled instanceof Page)
                submit((Page)labelled,true);
        }
        if (event instanceof CommentEvent) {
            ContentEntityObject pg = ((CommentEvent) event).getComment().getOwner();
            if (pg instanceof Page) {
                submit((Page) pg,true);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        worker.shutdown();
    }

    public void submit(Page page, boolean evictNow) {
        final Task t = new Task(page);
        if (evictNow)
            t.output.delete();

        // by the time event happens, the data appears not to be committed,
        // so we are delaying the execution of the task a bit
        worker.schedule(new Runnable() {
            public void run() {
                try {
                    t.output.delete();
                    t.execute();
                } catch (Exception e) {
                    LOGGER.warn("Failed to generate " + t.url, e);
                }
            }
        },3,TimeUnit.SECONDS);
    }

    private static final Logger LOGGER = Logger.getLogger(StaticPageGenerator.class);

    private static final ThreadFactory DAEMON_THREAD_FACTORY = new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }
    };


    private static File getBaseDir() {
        String dir = System.getenv("STATIC_CACHE_DIR");
        if (dir==null)  dir = "/tmp";
        return new File(dir);
    }
}
