package bear.main.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.logging.log4j.message.Message;

public class ObjectMessage implements Message {

    private static final long serialVersionUID = -5903272448334166185L;

    public ObjectMessage() {
    }

    /**
     * Returns the formatted object message.
     * @return the formatted object message.
     */
    @Override
    @JsonIgnore
    public String getFormattedMessage() {
        return null;
    }

    /**
     * Returns the object formatted using its toString method.
     * @return the String representation of the object.
     */
    @Override
    @JsonIgnore
    public String getFormat() {
        return null;
    }

    /**
     * Returns the object as if it were a parameter.
     * @return The object.
     */
    @Override
    @JsonIgnore
    public Object[] getParameters() {
        return null;
    }

    /**
     * Gets the message if it is a throwable.
     *
     * @return the message if it is a throwable.
     */
    @Override
    @JsonIgnore
    public Throwable getThrowable() {
        return null;
    }
}