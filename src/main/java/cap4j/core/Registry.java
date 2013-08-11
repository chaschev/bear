package cap4j.core;

import cap4j.task.Task;
import com.chaschev.chutils.util.OpenBean2;
import org.apache.commons.lang3.StringUtils;

/**
 * User: achaschev
 * Date: 8/12/13
 * Time: 1:04 AM
 */
public enum Registry {
    INSTANCE;

    public Task getByName(String name){
        String className;
        String taskName;

        if(!name.contains(".")){
            className = "CapConstants";
        }else {
            className = StringUtils.substringBefore(name, ".");
        }

        taskName = StringUtils.substringAfter(name, ".");

        return getTask(className, taskName);
    }

    private Task getTask(String className, String taskName) {
        if(!className.equals("CapConstants")){
            throw new UnsupportedOperationException("todo");
        }

        final Task task = (Task) OpenBean2.getFieldValue2(CapConstants.class, taskName);

        return task;
    }
}
