package bear.plugins.misc;

import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.task.*;
import com.google.common.base.Optional;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.TimeZone;

import static bear.session.BearVariables.joinPath;
import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ReleasesPlugin extends Plugin {
    public static final DateTimeZone GMT = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT"));
    public static final DateTimeFormatter RELEASE_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd.HHmmss").withZone(GMT);

    public final DynamicVariable<PendingRelease> pendingRelease = undefined();

    // not present when: there is no activation for pending release OR when there is no release
    public final DynamicVariable<Optional<Release>> activatedRelease = dynamic(new Fun<AbstractContext, Optional<Release>>() {
        @Override
        public Optional<Release> apply(AbstractContext $) {
            return $.var(session).getCurrentRelease();
        }
    });

    public final DynamicVariable<Optional<Release>> rollbackToRelease = undefined();

    public final DynamicVariable<String>
        dirName = newVar("releases"),
        currentDirName = newVar("current"),
        releaseName = dynamic(new Fun<AbstractContext, String>() {
            @Override
            public String apply(AbstractContext $) {
                return RELEASE_FORMATTER.print(new DateTime()) + ".GMT";
            }
        }).memoizeIn(GlobalContext.class),
        pendingName = newVar("pending_"),
        pendingReleaseName = concat("pending_", releaseName),
        path = joinPath(bear.applicationPath, dirName),
        releasesJsonPath = joinPath(path, "releases.json"),
        currentReleaseLinkPath = joinPath(path, currentDirName),
        releasePath = joinPath(path, releaseName),
        pendingReleasePath = concat(path, "/pending_", releaseName);

    public final DynamicVariable<Integer>
        keepXReleases = newVar(5);

    public final DynamicVariable<Boolean>
        cleanPending = newVar(true),
        manualRollback = newVar(false);

    public final DynamicVariable<Releases> session = dynamic(new Fun<SessionContext, Releases>() {
        @Override
        public Releases apply(SessionContext $) {
            return $.wire(new Releases($, ReleasesPlugin.this)).load();
        }
    }).memoizeIn(SessionContext.class);

    public ReleasesPlugin(GlobalContext global) {
        super(global);
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }

    public TaskDef<Task> findReleaseToRollbackTo(final String labelOrPath) {
        return new TaskDef<Task>(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                Releases session = $.var(ReleasesPlugin.this.session);
                Optional<Release> release = session.findAny(labelOrPath);

                if(!release.isPresent()){
                    return TaskResult.error("release not found: " + labelOrPath + " available releases:\n " + session.show());
                }

                $.putConst(rollbackToRelease, release);

                return TaskResult.OK;
            }
        });
    }
}
