package bear.plugins;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.except.ValidationException;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static chaschev.lang.Predicates2.contains;
import static com.google.common.base.Predicates.or;
import static com.google.common.base.Splitter.on;
import static com.google.common.collect.Iterables.find;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DownloadPlugin extends Plugin{
    public final DynamicVariable<Boolean> preferRemoteToLocal = Variables.newVar(true);

    public DownloadPlugin(GlobalContext global) {
        super(global);
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }

    // phase 1 - check who has the file. if none => download, so one of these has it
    // phase 2 - each party downloads it from the source
    // add multitask for it

    public static abstract class DownloadSupplier{
        String relativeCachePath;  // relativeTo   bear.downloadDirPath

        protected DownloadSupplier(String relativeCachePath) {
            this.relativeCachePath = relativeCachePath;
        }

        public boolean exists(SessionContext $){
            return $.sys.exists(absCachePath($));
        }

        protected String absCachePath(SessionContext $) {
            return $.joinPath($.bear.downloadDirPath, relativeCachePath);
        }

        public abstract Result download(SessionContext $);
    }

    // uploads a file from localhost
    public static class LocalDownloadSupplier extends DownloadSupplier{
        File file;

        protected LocalDownloadSupplier(String relativeCachePath) {
            super(relativeCachePath);
        }

        @Override
        public Result download(SessionContext $) {
            return $.sys.upload(absCachePath($), file);
        }
    }
    
    public static class WgetDownloadSupplier extends DownloadSupplier{
        String url;

        public WgetDownloadSupplier(String relativeCachePath, String url) {
            super(relativeCachePath);
            this.url = url;
        }

        @Override
        public Result download(SessionContext $){
            CommandLineResult result = $.sys.script()
                .cd(absCachePath($))
                .line().timeoutMin(60).addRaw("wget %s", url).build()
                .run();

            CommandLineResult run = result;

            Predicate<String> errorPredicate = or(contains("404 Not Found"), contains("ERROR"));

            if(errorPredicate.apply(run.output)){
                throw new ValidationException("Error during download of " + url +
                    ": " + find(on('\n').split(run.output), errorPredicate));
            }
            return result.getResult();
        }
    }

    private static final class CachedResult extends TaskResult{
        boolean cached;

        public CachedResult(boolean cached) {
            super(Result.OK);

            this.cached = cached;
        }
    }

    private static final class PartyWithFileResult extends TaskResult{
        int partyIndex;

        private PartyWithFileResult(int partyIndex) {
            super(Result.OK);
            this.partyIndex = partyIndex;
        }
    }

    private final SingleTaskSupplier<DownloadSupplier,TaskResult> singleTaskSupplier = new SingleTaskSupplier<DownloadSupplier, TaskResult>() {
        @Override
        public Task<DownloadSupplier, TaskResult> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<DownloadSupplier, TaskResult> def) {
            TaskCallable<DownloadSupplier, TaskResult> taskCallable = new TaskCallable<DownloadSupplier, TaskResult>() {
                @Override
                public TaskResult call(final SessionContext $, final Task<DownloadSupplier, TaskResult> task) throws Exception {
                    final ImmutableList<SessionContext> parties = task.getGrid().parties();

                    final DownloadSupplier downloadSupplier = task.getInput();

                    ListenableFuture<PartyWithFileResult> future = task.callOnce(new Callable<PartyWithFileResult>() {
                        @Override
                        public PartyWithFileResult call() throws Exception {
                            int i;
                            for (i = 0; i < parties.size(); i++) {
                                SessionContext party = parties.get(i);

                                if (downloadSupplier.exists(party)) {
                                    break;
                                }
                            }

                            if (i == parties.size()) {
                                if (!downloadSupplier.download($).ok()) {
                                    throw new RuntimeException("could not download with " + downloadSupplier);
                                }

                                i = task.getPhaseParty().getIndex();
                            }

                            return new PartyWithFileResult(i);
                        }
                    });

                    PartyWithFileResult result = future.get(10, TimeUnit.MINUTES);

                    if (!result.ok()) {
                        return result;
                    }

                    if (result.partyIndex == task.getPhaseParty().getIndex()) {
                        return TaskResult.OK;
                    }

                    if (downloadSupplier.exists($)) {
                        return TaskResult.OK;
                    }

                    Result r = $.sys.scpFrom(parties.get(result.partyIndex), downloadSupplier.relativeCachePath, null,
                        downloadSupplier.relativeCachePath + "/*");

                    return new TaskResult(r);
                }
            };
            return new Task<DownloadSupplier, TaskResult>(parent, taskCallable);
        }
    };

    public final TaskDef<DownloadSupplier, TaskResult> downloadTask = new TaskDef<DownloadSupplier, TaskResult>(singleTaskSupplier);
}
