package bear.plugins.misc;

import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.task.InstallationTask;
import bear.task.InstallationTaskDef;
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
    public final DynamicVariable<Optional<Release>> activatedRelease = dynamic(new Fun<Optional<Release>, AbstractContext>() {
        @Override
        public Optional<Release> apply(AbstractContext $) {
            return $.var(session).getCurrentRelease();
        }
    });

    public final DynamicVariable<Optional<Release>> rollbackToRelease = undefined();

    public final DynamicVariable<String>
        dirName = newVar("releases"),
        currentDirName = newVar("current"),
        releaseName = dynamic(new Fun<String, AbstractContext>() {
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
        cleanPending = newVar(true);

    public final DynamicVariable<Releases> session = dynamic(new Fun<Releases, SessionContext>() {
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
}
