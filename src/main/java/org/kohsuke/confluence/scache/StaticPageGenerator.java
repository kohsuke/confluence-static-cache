package org.kohsuke.confluence.scache;

import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.event.events.content.comment.CommentCreateEvent;
import com.atlassian.confluence.event.events.content.comment.CommentEvent;
import com.atlassian.confluence.event.events.content.comment.CommentRemoveEvent;
import com.atlassian.confluence.event.events.content.comment.CommentUpdateEvent;
import com.atlassian.confluence.event.events.content.page.PageCreateEvent;
import com.atlassian.confluence.event.events.content.page.PageEvent;
import com.atlassian.confluence.event.events.content.page.PageMoveEvent;
import com.atlassian.confluence.event.events.content.page.PageRemoveEvent;
import com.atlassian.confluence.event.events.content.page.PageRestoreEvent;
import com.atlassian.confluence.event.events.content.page.PageUpdateEvent;
import com.atlassian.confluence.event.events.label.LabelAddEvent;
import com.atlassian.confluence.event.events.label.LabelEvent;
import com.atlassian.confluence.event.events.label.LabelRemoveEvent;
import com.atlassian.confluence.labels.Labelable;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.event.Event;
import com.atlassian.event.EventListener;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
public class StaticPageGenerator implements EventListener {
    private final ScheduledExecutorService worker = new ScheduledThreadPoolExecutor(1,DAEMON_THREAD_FACTORY);
//    private final LinkedHashSet<Task> tasks = new LinkedHashSet<Task>();
    private final String confluenceUrl;
    /**
     * This is where the cache gets written.
     */
    private final File baseDir = getBaseDir();

    private final Timer regenerateAll = new Timer();
    private final SpaceManager spaceManager;
    private final HttpClient client;

    /**
     * username:password
     */
    private final String authentication;

    public class Task {
        final String url;
        final File output;
        private final String key;

        public Task(Page page) {
            key = page.getSpaceKey()+'/'+page.getTitle();
            url = confluenceUrl+page.getUrlPath();
            output = new File(baseDir,page.getSpaceKey()+'/'+page.getTitle()+".html");
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
            get.setRequestHeader("Authorization", "Basic " + Base64.encodeBase64String(authentication.getBytes()));

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

    public StaticPageGenerator(final PageManager pageManager, final SpaceManager spaceManager) throws IOException {
        this.spaceManager = spaceManager;

        client = new HttpClient();
//            client.getHostConfiguration().setHost("wiki2.jenkins-ci.org", 443,
//                    new Protocol("https", (ProtocolSocketFactory)new EasySSLProtocolSocketFactory(), 443));
        Protocol.registerProtocol("https", new Protocol("https", new EasySSLProtocolSocketFactory(), 443));

        Properties props = loadConfigFile();
        authentication = props.getProperty("authentication", "scanner:scanner");
        confluenceUrl = props.getProperty("url");

        regenerateAll.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                LOGGER.info("Rescheduling the generation of everything");
                Space space = spaceManager.getSpace("JENKINS");
                List<Page> pagesList = pageManager.getPages(space, true);
                for (Page page : pagesList) {
                    submit(page);
                }
            }
        }, TimeUnit.HOURS.toMillis(6), TimeUnit.HOURS.toMillis(6));
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

    public void handleEvent(Event event) {
        LOGGER.info("Handling "+event);
        if (event instanceof PageRemoveEvent) {
            new Task(((PageEvent) event).getPage()).delete();
        }
        if (event instanceof PageEvent) {
            submit(((PageEvent) event).getPage());
        }
        if (event instanceof LabelEvent) {
            Labelable labelled = ((LabelEvent) event).getLabelled();
            if (labelled instanceof Page)
                submit((Page)labelled);
        }
        if (event instanceof CommentEvent) {
            ContentEntityObject pg = ((CommentEvent) event).getComment().getOwner();
            if (pg instanceof Page) {
                submit((Page) pg);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        worker.shutdown();
    }

    public Class[] getHandledEventClasses() {
        return HANDLED_EVENTS;
    }

    public void submit(Page page) {
//        synchronized (tasks) {
//            Task t = new Task(page);
//            if (tasks.add(t)) {
//                t.output.delete(); // delete the stale cache until we regenerate the cache
//            }
//        }

        final Task t = new Task(page);
        t.output.delete();

        // by the time event happens, the data appears not to be committed,
        // so we are delaying the execution of the task a bit
        worker.schedule(new Runnable() {
            public void run() {
                Task task = pop();
                if (task==null)     return;

                try {
                    task.execute();
                } catch (Exception e) {
                    LOGGER.warn("Failed to generate " + task.url, e);
                }
            }

            private Task pop() {
                return t;
//                synchronized (tasks) {
//                    if (tasks.isEmpty())    return null;
//                    Iterator<Task> itr = tasks.iterator();
//                    Task r = itr.next();
//                    itr.remove();
//                    return r;
//                }
            }
        },3,TimeUnit.SECONDS);
    }

    private static final Logger LOGGER = Logger.getLogger(StaticPageGenerator.class.getName());

    private static final ThreadFactory DAEMON_THREAD_FACTORY = new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }
    };

    private final Class[] HANDLED_EVENTS = new Class[] {
            PageCreateEvent.class,
            PageRestoreEvent.class,
            PageUpdateEvent.class,
            PageMoveEvent.class,
            PageRemoveEvent.class,
            LabelAddEvent.class,
            LabelRemoveEvent.class,
            CommentCreateEvent.class,
            CommentUpdateEvent.class,
            CommentRemoveEvent.class
    };

    private static File getBaseDir() {
        String dir = System.getenv("STATIC_CACHE_DIR");
        if (dir==null)  dir = "/tmp";
        return new File(dir);
    }
}
