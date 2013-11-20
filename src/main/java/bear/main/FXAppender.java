package bear.main;

import bear.main.event.EventToUI;
import bear.main.event.LogEventToUI;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.message.Message;

import java.io.Serializable;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class FXAppender extends AbstractAppender{
    BearFX bearFX;

    protected FXAppender(String name, Filter filter, Layout<? extends Serializable> layout, BearFX bearFX) {
        super(name, filter, layout);
        this.bearFX = bearFX;
    }

    protected FXAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, BearFX bearFX) {
        super(name, filter, layout, ignoreExceptions);
        this.bearFX = bearFX;
    }

    @Override
    public void append(LogEvent event) {
        Message message = event.getMessage();

        //this might be a big buggy, because this event is not attached to a tree, e.g. has no parent
        if (message instanceof EventToUI) {
            EventToUI eventToUI = (EventToUI) message;
            eventToUI.setLevel(event.getLevel().intLevel());
            bearFX.sendMessageToUI(eventToUI);
            return;
        }

        String s = new String(getLayout().toByteArray(event));

        String threadName = event.getThreadName();
        bearFX.sendMessageToUI(
            new LogEventToUI(isSessionAddress(threadName) ? threadName : "status",
                s, event.getLevel()
            ));
    }

    //this is a hack
    private boolean isSessionAddress(String threadName) {
        return !(threadName.startsWith("JavaFX") || threadName.startsWith("Thread-") ||
        threadName.startsWith("pool-"));
    }
}
