package cap4j.strategy;

import cap4j.core.*;
import cap4j.session.Result;
import cap4j.session.VariableUtils;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static cap4j.core.CapConstants.*;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 * Time: 1:21 AM
 */

/**
 * mapping $releasePath/ROOT.war -> $webapps/ROOT.war
 */
public abstract class BaseStrategy {
    private static final Logger logger = LoggerFactory.getLogger(BaseStrategy.class);

    protected static CyclicBarrier prepareRemoteDataBarrier;
    protected static CyclicBarrier updateRemoteFilesBarrier;

    private static List<File> preparedLocalFiles;

    @Nullable
    private static String deployZipPath;
    private final CapConstants cap;

    protected SessionContext ctx;

    protected GlobalContext global;

    /**
     * Symlink rules.
     */
    protected SymlinkRules symlinkRules = new SymlinkRules();

    protected BaseStrategy(SessionContext ctx, GlobalContext global) {
        this.ctx = ctx;
        this.global = global;
        this.cap = global.cap;
    }

    public static void setBarriers(Stage stage, final GlobalContext global) {
        final SessionContext localCtx = global.localCtx;
        prepareRemoteDataBarrier = new CyclicBarrier(stage.getEnvironments().size(), new Runnable() {
            @Override
            public void run() {
                deployZipPath = null;

                global.console().stopRecording();

                logger.info("20: preparing local files");
                preparedLocalFiles = global.var(newStrategy).step_20_prepareLocalFiles(localCtx);

                if(preparedLocalFiles.isEmpty()) return;

                String tempDir = global.local().newTempDir();

                logger.info("20: zipping {} files of total size {} to: {}", preparedLocalFiles.size(),
                    byteCountToDisplaySize(countSpace(preparedLocalFiles)), tempDir);

                deployZipPath = localCtx.system.joinPath(tempDir, "deploy.zip");

                global.local().zip(deployZipPath, Lists.transform(preparedLocalFiles, new Function<File, String>() {
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

                global.var(newStrategy).step_50_whenRemoteUpdateFinished(localCtx);
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
            //step_20_prepareLocalFiles goes here hidden inside the barrier

            logger.info("30: copying files to hosts");
            _step_30_copyFilesToHosts();

            logger.info("40: updating remote files");
            _step_40_updateRemoteFiles();

            updateRemoteFilesBarrier.await(240, TimeUnit.SECONDS);

            return Result.OK;
        } catch (Exception e) {
            logger.warn("", e);
            return Result.ERROR;
        }
    }


    /**
     * Chance to ask all the hosts for the data. Waits until everyone is ready
     */
    protected void step_10_getPrepareRemoteData(){
        logger.info("10: skipping customization");
    }

    /**
     * This operation is single0threaded.
     *
     * @return List of files which will be zipped and copied to the remote hosts. If it is empty, copying is skipped.
     * @param localCtx
     */
    protected List<File> step_20_prepareLocalFiles(SessionContext localCtx){
        logger.info("20: skipping customization");
        return Collections.emptyList();
    }

    private void _step_30_copyFilesToHosts(){
        updateReleasesDirs();

        if(isCopyingZip()){
            ctx.system.upload(ctx.var(cap.releasePath), new File(deployZipPath));
        }

        step_30_copyFilesToHosts();
    }

    private void updateReleasesDirs() {
        ctx.system.mkdirs(ctx.var(cap.releasePath));
        int keepX = ctx.var(keepXReleases);

        if(keepX > 0){
            final Releases releases = ctx.var(cap.getReleases);
            List<String> toDelete = releases.listToDelete(keepX);

            ctx.system.rmCd(ctx.var(cap.releasesPath),
                toDelete.toArray(new String[toDelete.size()]));
        }
    }

    protected void step_30_copyFilesToHosts(){
        logger.info("30: skipping customization");
    }

    private void _step_40_updateRemoteFiles(){
        if(isCopyingZip()){
            ctx.system.unzip(
                ctx.joinPath(ctx.var(cap.releasePath), "deploy.zip"), null
            );
        }

        step_40_updateRemoteFiles();

        logger.info("creating {} symlinks...", symlinkRules.entries.size());

        for (SymlinkEntry entry : symlinkRules.entries) {
            String srcPath;

            srcPath = ctx.var(VariableUtils.joinPath("symlinkSrc", cap.currentPath, entry.sourcePath));

            ctx.system.link(srcPath, ctx.var(entry.destPath), entry.owner);
        }

        writeRevision();
    }

    protected Result writeRevision(){
        return ctx.system.writeString(ctx.joinPath(cap.releasePath, "REVISION"), ctx.var(cap.realRevision));
    }

    protected void step_40_updateRemoteFiles(){
        logger.info("40: skipping customization");
    }

    /**
     *
     * @param localCtx
     */
    protected void step_50_whenRemoteUpdateFinished(SessionContext localCtx){
        logger.info("50: skipping customization");
    }

    private static boolean isCopyingZip() {
        return deployZipPath != null;
    }

    public SymlinkRules getSymlinkRules() {
        return symlinkRules;
    }
}
