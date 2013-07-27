package cap4j.task;

import cap4j.session.Result;
import com.google.common.collect.Lists;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public class DefaultTasks {
    public static final Task<Task.TaskResult> DEPLOY_RESTART_APP_TASK = new Task<Task.TaskResult>() {

    }.setDependsOnTasks(
    );

    public static final Task<Task.TaskResult> DEPLOY_SYMLINK_TASK = new Task<Task.TaskResult>() {

        }.setDependsOnTasks(
    );

    public static final Task<Task.TaskResult> FINALIZE_UPDATE_TASK = new Task<Task.TaskResult>() {

    }.setDependsOnTasks(
    );


    public static final Task<Task.TaskResult> UPDATE_CODE_TASK = new Task<Task.TaskResult>() {

        }.setDependsOnTasks(
    );


    public static final Task<Task.TaskResult> UPDATE_TASK = new Task<Task.TaskResult>() {
        }.setDependsOnTasks(
    );

    public static final Task<Task.TaskResult> DEPLOY_TASK = new Task<Task.TaskResult>() {

    }.setDependsOnTasks(UPDATE_TASK);
}
