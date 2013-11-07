/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.strategy;

import bear.core.*;
import bear.session.BearVariables;
import bear.session.Result;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.TaskRunner;
import bear.vcs.CommandLineResult;
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

import static bear.context.DependencyInjection.inject;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 * Time: 1:21 AM
 */

/**
 * mapping $releasePath/ROOT.war -> $webapps/ROOT.war
 */
public abstract class DeployStrategyTaskDef extends TaskDef<Task> {
    private static final Logger logger = LoggerFactory.getLogger(DeployStrategyTaskDef.class);

    @Nullable
    private String deployZipPath;
    private Bear bear;

    protected GlobalContext global;

    protected CyclicBarrier prepareRemoteDataBarrier;
    protected CyclicBarrier updateRemoteFilesBarrier;

    private List<File> preparedLocalFiles;



    /**
     * Symlink rules.
     */
    protected SymlinkRules symlinkRules = new SymlinkRules();

    protected DeployStrategyTaskDef(SessionContext $) {
        super("DeployStrategy", $);

        inject(this, $);
//        $.wire(this);
//        this.global = $.getGlobal();
//        this.bear = global.bear;

        final SessionContext localCtx = global.localCtx;

        Stage stage = localCtx.var(bear.getStage);

        prepareRemoteDataBarrier = new CyclicBarrier(stage.getEnvironments().size(), new Runnable() {
            @Override
            public void run() {
                deployZipPath = null;

                global.console().stopRecording();

                logger.info("20: preparing local files");
                preparedLocalFiles = global.var(bear.getStrategy).step_20_prepareLocalFiles(localCtx);

                if(preparedLocalFiles.isEmpty()) return;

                String tempDir = global.local().newTempDir();

                logger.info("20: zipping {} files of total size {} to: {}", preparedLocalFiles.size(),
                    byteCountToDisplaySize(countSpace(preparedLocalFiles)), tempDir);

                deployZipPath = global.localCtx.sys.joinPath(tempDir, "deploy.zip");

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

                global.var(bear.getStrategy).step_50_whenRemoteUpdateFinished(localCtx);
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

    @Override
    public Task<TaskDef> newSession(SessionContext $, final Task parent) {
        return new DeployTask(parent, $);
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

    private boolean isCopyingZip() {
        return deployZipPath != null;
    }

    public SymlinkRules getSymlinkRules() {
        return symlinkRules;
    }

    public class DeployTask extends Task<TaskDef> {
        public DeployTask(Task<TaskDef> parent, SessionContext $) {
            super(parent, DeployStrategyTaskDef.this, $);
        }

        @Override
        protected final TaskResult exec(TaskRunner runner) {
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

                return TaskResult.OK;
            } catch (Exception e) {
                logger.warn("", e);
                return new CommandLineResult(e.toString(), Result.ERROR);
            }
        }

        private void _step_30_copyFilesToHosts(){
            updateReleasesDirs();

            if(isCopyingZip()){
                $.sys.upload($(bear.releasePath), new File(deployZipPath));
            }

            step_30_copyFilesToHosts();
        }

        private void updateReleasesDirs() {
            $.sys.mkdirs($(bear.releasePath));
            int keepX = $(bear.keepXReleases);

            if(keepX > 0){
                final Releases releases = $(bear.getReleases);
                List<String> toDelete = releases.listToDelete(keepX);

                $.sys.rmCd($(bear.releasesPath),
                    toDelete.toArray(new String[toDelete.size()]));
            }
        }

        protected void step_30_copyFilesToHosts(){
            logger.info("30: skipping customization");
        }

        private void _step_40_updateRemoteFiles(){
            if(isCopyingZip()){
                $.sys.unzip(
                    $.joinPath($(bear.releasePath), "deploy.zip"), null
                );
            }

            step_40_updateRemoteFiles();

            logger.info("creating {} symlinks...", symlinkRules.entries.size());

            for (SymlinkEntry entry : symlinkRules.entries) {
                String srcPath;

                srcPath = $(BearVariables.joinPath("symlinkSrc", bear.currentPath, entry.sourcePath));

                $.sys.link(srcPath, $(entry.destPath), entry.owner);
            }

            writeRevision();
        }

        protected Result writeRevision(){
            return $.sys.writeString($.joinPath(bear.releasePath, "REVISION"), $(bear.realRevision));
        }
    }
}
