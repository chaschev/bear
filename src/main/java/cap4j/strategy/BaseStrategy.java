package cap4j.strategy;

import cap4j.GlobalContext;
import cap4j.VarContext;
import cap4j.Stage;
import cap4j.session.Result;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static cap4j.CapConstants.newStrategy;
import static cap4j.CapConstants.releasePath;
import static cap4j.GlobalContext.local;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 * Time: 1:21 AM
 */
public abstract class BaseStrategy {
    private static final Logger logger = LoggerFactory.getLogger(BaseStrategy.class);

    protected static CyclicBarrier prepareRemoteDataBarrier;
    protected static CyclicBarrier updateRemoteFilesBarrier;

    private static List<File> preparedLocalFiles;

    @Nullable
    private static String deployZipPath;

    protected VarContext ctx;

    public static void setBarriers(Stage stage, final VarContext localCtx) {
        prepareRemoteDataBarrier = new CyclicBarrier(stage.getEnvironments().size(), new Runnable() {
            @Override
            public void run() {
                deployZipPath = null;

                GlobalContext.console().stopRecording();

                logger.info("20: preparing local files");
                preparedLocalFiles = localCtx.gvar(newStrategy).step_20_prepareLocalFiles(localCtx);

                if(preparedLocalFiles.isEmpty()) return;

                String tempDir = local().newTempDir();

                logger.info("20: zipping {} files of total size {} to: {}", preparedLocalFiles.size(),
                    byteCountToDisplaySize(countSpace(preparedLocalFiles)), tempDir);

                deployZipPath = localCtx.system.joinPath(tempDir, "deploy.zip");

                local().zip(deployZipPath, Iterables.transform(preparedLocalFiles, new Function<File, String>() {
                    public String apply(File f) {
                        return f.getAbsolutePath();
                    }
                }));
            }
        });

        updateRemoteFilesBarrier = new CyclicBarrier(stage.getEnvironments().size(), new Runnable() {
            @Override
            public void run() {
                logger.info("50: remote update is done now");

                localCtx.gvar(newStrategy).step_50_whenRemoteUpdateFinished(localCtx);
            }
        });
    }

    private static long countSpace(List<File> files) {
        long size = 0;

        for (File file : files) {
            size += file.length();
        }
        return size;
    }

    /**
     * Inside a thread now.
     */
    public Result deploy(){
        try {
            Preconditions.checkNotNull(prepareRemoteDataBarrier, "prepareRemoteDataBarrier is null");
            Preconditions.checkNotNull(updateRemoteFilesBarrier, "updateRemoteFilesBarrier is null");

            logger.info("10: preparing remote data");
            step_10_getPrepareRemoteData();

            logger.info("10: waiting for other threads to finish ({}/{})", prepareRemoteDataBarrier.getNumberWaiting() + 1, prepareRemoteDataBarrier.getParties());
            prepareRemoteDataBarrier.await(120, TimeUnit.SECONDS);
            //now prepareRemoteDataBarrier should take it to a single-threaded local preparation
            //step_20_prepareLocalFiles goes here

            _step_30_copyFilesToHosts();
            _step_40_updateRemoteFiles();

            updateRemoteFilesBarrier.await(240, TimeUnit.SECONDS);

            return Result.OK;
        } catch (Exception e) {
            return Result.ERROR;
        }
    }


    /**
     * Chance to ask all the hosts for the data. Waits until everyone is ready
     */
    protected abstract void step_10_getPrepareRemoteData();

    /**
     * This operation is single0threaded.
     *
     * @return List of files which will be zipped and copied to the remote hosts. If it is empty, copying is skipped.
     * @param localCtx
     */
    protected abstract List<File> step_20_prepareLocalFiles(VarContext localCtx);

    private void _step_30_copyFilesToHosts(){
        if(isCopyingZip()){
            ctx.system.scpLocal(ctx.gvar(releasePath), new File(deployZipPath));
        }

        step_30_copyFilesToHosts();
    }

    protected abstract void step_30_copyFilesToHosts();

    private void _step_40_updateRemoteFiles(){
        if(isCopyingZip()){
            ctx.system.unzip(
                ctx.joinPath(ctx.gvar(releasePath), "deploy.zip"), null
            );
        }

        step_40_updateRemoteFiles();
    }

    protected abstract void step_40_updateRemoteFiles();

    /**
     *
     * @param localCtx
     */
    protected abstract void step_50_whenRemoteUpdateFinished(VarContext localCtx);

    private static boolean isCopyingZip() {
        return deployZipPath != null;
    }


}
