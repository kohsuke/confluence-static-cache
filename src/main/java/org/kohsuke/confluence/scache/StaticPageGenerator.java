package org.kohsuke.confluence.scache;

import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.event.events.content.comment.CommentEvent;
import com.atlassian.confluence.event.events.content.page.PageEvent;
import com.atlassian.confluence.event.events.content.page.PageRemoveEvent;
import com.atlassian.confluence.event.events.label.LabelEvent;
import com.atlassian.confluence.labels.Label;
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
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        private boolean nocache;

        public Task(Page page) {
            key = page.getSpaceKey()+'/'+page.getTitle();
            url = configurationManager.getRetrievalUrl()+page.getUrlPath();
            output = new File(getCacheDir(),page.getSpaceKey()+'/'+page.getTitle()+".html");
            for (Label l : page.getLabels()) {
                if (l.getName().equals("nocache"))
                    nocache=true;
            }
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
            if (!shouldCache()) {
                LOGGER.info("Deleting cache of "+key);
                delete();
                return;
            }

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

        public boolean shouldCache() {
            return !nocache;
        }
    }

    private File getCacheDir() {
        return new File(configurationManager.getRootPath());
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

        worker.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    regenerateAll();
                } finally {// scheduleAtFixedRate will stop scheduling if there's any error once.
                    worker.schedule(this,6,TimeUnit.HOURS);
                }
            }
        }, 6, TimeUnit.HOURS);
    }

    public void regenerateAll() {
        LOGGER.info("Rescheduling the generation of everything");
        for (Space space : spaceManager.getAllSpaces()) {
            if (space.isPersonal()) continue;   // don't care about personal space

            List<Page> pagesList = pageManager.getPages(space, true);

            File dir = new File(getCacheDir(),space.getKey());
            String[] files = dir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".html");
                }
            });
            if (files==null)    files = new String[0];
            Set<String> existingCaches = new HashSet<String>(Arrays.asList(files));

            for (Page page : pagesList) {
                Task t = submit(page,false);
                if (t.shouldCache())
                    existingCaches.remove(t.output.getName());
            }

            // delete all files that aren't cached
            for (String garbage : existingCaches) {
                new File(getCacheDir(),garbage).delete();
            }
        }
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

    public Task submit(Page page, boolean evictNow) {
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
        },10,TimeUnit.SECONDS);

        return t;
    }

    private static final Logger LOGGER = Logger.getLogger(StaticPageGenerator.class);

    private static final ThreadFactory DAEMON_THREAD_FACTORY = new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }
    };
}
