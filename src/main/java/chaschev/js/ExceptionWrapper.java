package chaschev.js;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class ExceptionWrapper {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionWrapper.class);
    public final boolean isExceptionWrapper = true;

    public String stackTrace;

    public ExceptionWrapper(Throwable e) {
        this.stackTrace = e.toString();
        logger.warn("", e);
    }

    public ExceptionWrapper(Throwable e, String message) {
        this.stackTrace = e.toString() + ": " + message;

        logger.warn(message, e);
    }
}
