package bear.core;

import bear.console.ConsolesDivider;
import bear.console.GroupDivider;
import bear.main.BearFX;
import bear.main.event.PhaseFinishedEventToUI;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import chaschev.util.CatchyCallable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class BearScriptPhase {
    public final String id = SessionContext.randomId();
    TaskDef<Task> taskDef;
    final AtomicInteger partiesArrived = new AtomicInteger();
    public final AtomicInteger partiesOk = new AtomicInteger();

    final AtomicLong minimalOkDuration = new AtomicLong(-1);

    BearFX bearFX;

    GroupDivider<SessionContext> groupDivider;

    public final long startedAtMs = System.currentTimeMillis();

    final int partiesCount;

    public int partiesPending;
    public int partiesFailed = 0;

    public BearScriptPhase(TaskDef<Task> taskDef, BearFX bearFX, GroupDivider<SessionContext> groupDivider) {
        this.taskDef = taskDef;
        this.bearFX = bearFX;
        this.partiesCount = groupDivider.getEntries().size();
        this.groupDivider = groupDivider;
    }

    public String getName() {
        return taskDef.getName();
    }

    public String getDisplayName() {
        return taskDef.getDisplayName();
    }

    public void addArrival(SessionContext $, final long duration, TaskResult result) {
        groupDivider.addArrival($);

        if (result.ok()) {
            partiesOk.incrementAndGet();

            if (minimalOkDuration.compareAndSet(-1, duration)) {
                $.getGlobal().scheduler.schedule(new CatchyCallable<Void>(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        boolean haveHangUpJobs;
                        boolean alreadyFinished;

                        if (partiesArrived.compareAndSet(partiesCount, -1)) {
                            alreadyFinished = false;
                            haveHangUpJobs = false;
                        } else {
                            if (partiesArrived.compareAndSet(-1, -1)) {
                                haveHangUpJobs = false;
                                alreadyFinished = true;
                            } else {
                                alreadyFinished = false;
                                haveHangUpJobs = true;
                            }
                        }

                        if (!alreadyFinished) {
                            sendPhaseResults(duration);
                        }
                        return null;
                    }
                }), duration * 3, TimeUnit.MILLISECONDS);
            }
        }


        partiesArrived.incrementAndGet();

        partiesPending = partiesCount - partiesArrived.get();
        partiesFailed = partiesArrived.get() - partiesOk.get();

        if (partiesArrived.compareAndSet(partiesCount, -1)) {
            sendPhaseResults(duration);
        }
    }

    private void sendPhaseResults(long duration) {
        List<ConsolesDivider.EqualityGroup> groups = groupDivider.divideIntoGroups();

        SessionContext.ui.info(
            //todo check this name is a one line desc
            new PhaseFinishedEventToUI(duration, groups, getName())
                .setParentId(id));
    }
}
