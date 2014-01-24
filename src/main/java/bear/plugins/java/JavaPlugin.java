/*
 * Copyright (C) 2014 Andrey Chaschev.
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

package bear.plugins.java;

import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.BearMain;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.fx.DownloadFxApp;
import bear.main.phaser.OnceEnteredCallable;
import bear.plugins.ZippedToolPlugin;
import bear.session.DynamicVariable;
import bear.task.*;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static bear.session.BearVariables.joinPath;
import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class JavaPlugin extends ZippedToolPlugin {
    private static final Logger logger = LoggerFactory.getLogger(JavaPlugin.class);

    public DynamicVariable<String>
        localDistrPath,
        jenkinsCacheUri = newVar("http://ftp-nyc.osuosl.org/pub/jenkins/updates/updates/hudson.tools.JDKInstaller.json"),
        oracleUser = undefined(),
        oraclePassword = undefined()
    ;

    public JavaPlugin(GlobalContext global) {
        super(global);

        LoggerFactory.getLogger("log").warn("JavaPlugin initialized");

        toolname.defaultTo("jdk");
        version.desc("i.e. 7u51");

        versionName.setEqualTo(dynamic(new Fun<AbstractContext, String>() {
            @Override
            public String apply(AbstractContext $) {
                //jdk-7u40-linux-x64
                return "jdk-" + $.var(version) + "-" + ($.var(bear.isNativeUnix) ? "linux" : "windows") + "-x64";
            }
        }).temp());

//        versionName.desc("distribution file name with extension, i.e jdk-7u40-linux-x64");
        distrFilename.setEqualTo(dynamic(new Fun<AbstractContext, String>() {
            @Override
            public String apply(AbstractContext $) {
                String v = $.var(version);
                char ch = v.charAt(0);
                switch (ch){
                    case '5':
                    case '6':
                        return $.var(versionName) + ".bin";
                    case '7':
                    case '8':
                    case '9':
                        return  $.var(versionName) + ".tar.gz";
                }

                throw new IllegalStateException("not supported: " + v);
            }
        }).temp());

        localDistrPath = joinPath(myDirPath, distrFilename);
    }


    public DynamicVariable<Boolean>
        acceptLicense = undefined();

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>(new SingleTaskSupplier<Object, TaskResult<?>>() {
        final OnceEnteredCallable<File> once = new OnceEnteredCallable<File>();

        @Override
        public Task<Object, TaskResult<?>> createNewSession(SessionContext $, Task<Object, TaskResult<?>> parent, TaskDef<Object, TaskResult<?>> def) {

            return new ZippedTool(parent, (InstallationTaskDef) def, $) {
                @Override
                protected TaskResult<?> exec(SessionRunner runner) {
                    try {
                        clean();

                        String distrFilenameS = $.var(distrFilename);

                        final File localDFile = new File(global.localCtx.var(myDirPath), distrFilenameS);

                        if (!localDFile.exists()) {
                            logger.info("jdk is not found in cache: {}, trying to download...", localDFile);

                            Preconditions.checkState(BearMain.hasFx(), "we are sorry, Java 1.7+ is required to download JDK");

                            once.runOnce(new Callable<File>() {
                                @Override
                                public File call() throws Exception {
                                    logger.info("downloading jdk: {}", once);
                                    try {
                                        if(!global.var(acceptLicense)){
                                            throw new RuntimeException("you need to set " + acceptLicense.name() + " to download JDK from Oracle's site");
                                        }
                                    } catch (RuntimeException e) {
                                        throw new RuntimeException("you need to set " + acceptLicense.name() + " to download JDK from Oracle's site");
                                    }

                                    if(global.isDefined(oracleUser)){
                                        DownloadFxApp.oracleUser = global.var(oracleUser);
                                        DownloadFxApp.oraclePassword = global.var(oraclePassword);
                                    }

                                    DownloadFxApp.version = global.var(version);
                                    DownloadFxApp.tempDestDir = localDFile.getParentFile();
                                    DownloadFxApp.miniMode = true;

                                    if (global.var(bear.useUI)) {
                                        BearMain.launchUi();
                                        DownloadFxApp.launchUI();
                                    } else {
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                DownloadFxApp.main(new String[0]);
                                            }
                                        }, "download-jdk-fx-launcher").start();
                                    }

                                    DownloadFxApp downloadFxApp = DownloadFxApp.getInstance()
                                        .awaitDownload(1, TimeUnit.HOURS);

                                    DownloadFxApp.DownloadResult r = downloadFxApp.downloadResult.get();

                                    downloadFxApp.stop();

                                    if (r == null) {
                                        throw new RuntimeException("download result is null");
                                    }

                                    if (!r.ok) {
                                        throw new RuntimeException("download error: " + r.message);
                                    }

                                    if(r.file.getCanonicalPath().equals(localDFile.getPath())){
                                        r.file.renameTo(localDFile);
                                    }

                                    return localDFile;
                                }
                            }).get();

                            if(!localDFile.exists()){
                                return new DependencyResult($(toolname))
                                    .add("unable to download JDK, you may download it from Oracle.com site and place it to " + localDFile.getAbsolutePath());
                            }
                        }

                        $.sys.upload($(myDirPath), localDFile);

                        extractToHomeDir();

                        shortCut("java", "bin/java");
                        shortCut("javah", "bin/javah");
                        shortCut("javac", "bin/javac");

                        return verify();
                    } catch (InterruptedException e) {
                        throw Exceptions.runtime(e);
                    } catch (ExecutionException e) {
                        throw Exceptions.runtime(e);
                    }
                }

                @Override
                protected String extractVersion(String output) {
                    String version = StringUtils.substringBetween(
                        output,
                        "java version \"", "\"");

                    String update = StringUtils.substringAfterLast(version, "_");

                    String major = StringUtils.substringBetween(version, ".");

                    return major + "u" + update;
                }

                @Override
                protected String createVersionCommandLine() {
                    return "java -version";
                }
            };
        }
    });







    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }
}
