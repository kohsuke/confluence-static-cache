package org.kohsuke.confluence.scache;

import com.atlassian.confluence.event.events.content.comment.CommentCreateEvent;
import com.atlassian.confluence.event.events.content.comment.CommentRemoveEvent;
import com.atlassian.confluence.event.events.content.comment.CommentUpdateEvent;
import com.atlassian.confluence.event.events.content.page.PageCreateEvent;
import com.atlassian.confluence.event.events.content.page.PageMoveEvent;
import com.atlassian.confluence.event.events.content.page.PageRemoveEvent;
import com.atlassian.confluence.event.events.content.page.PageRestoreEvent;
import com.atlassian.confluence.event.events.content.page.PageUpdateEvent;
import com.atlassian.confluence.event.events.label.LabelAddEvent;
import com.atlassian.confluence.event.events.label.LabelRemoveEvent;
import com.atlassian.event.Event;
import com.atlassian.event.EventListener;

/**
 * @author Kohsuke Kawaguchi
 */
public class EventListenerImpl implements EventListener {
    private final StaticPageGenerator staticPageGenerator;

    public EventListenerImpl(StaticPageGenerator staticPageGenerator) {
        this.staticPageGenerator = staticPageGenerator;
    }

    @Override
    public void handleEvent(Event event) {
        staticPageGenerator.onEvent(event);
    }

    @Override
    public Class[] getHandledEventClasses() {
        return HANDLED_EVENTS;
    }

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
}
