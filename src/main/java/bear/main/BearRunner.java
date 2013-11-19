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

package bear.main;

import bear.core.*;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class BearRunner {
    private FXConf configurator;
    private IBearSettings bearSettings;
    private GlobalContextFactory factory;
    private GlobalContext global;

    private Supplier<? extends Script> scriptSupplier;

    @Deprecated
    private String bearScript;
    private boolean shutdownAfterRun;
    private CompositeTaskRunContext runContext;
    private boolean await = true;
    private transient Script script;

    public BearRunner(IBearSettings bearSettings, Script script, GlobalContextFactory factory) {
        this(bearSettings, Suppliers.ofInstance(script), factory);
    }

    public BearRunner(IBearSettings bearSettings, Supplier<? extends Script> scriptSupplier, GlobalContextFactory factory) {
        try {
            this.scriptSupplier = scriptSupplier;
            this.bearSettings = bearSettings;
            this.factory = factory;
            this.global = bearSettings.getGlobal();

        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public BearRunner(FXConf configurator, String bearScript) {
        this.configurator = configurator;
        this.bearScript = bearScript;
        this.global = configurator.getGlobal();

        try {
            this.bearSettings = configurator.newSettings();

            factory = configurator.getFactory();

//            init();
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

//    public BearRunner(FXConf configurator) {
//        try {
//            this.bearSettings = configurator.newSettings();
//            this.script = (Script) configurator.getScriptToRun().get().aClass.newInstance();
//
//            factory = configurator.getFactory();
//
//            init();
//        } catch (Exception e) {
//            throw Exceptions.runtime(e);
//        }
//    }

    public final BearRunner init() throws Exception {
        Preconditions.checkNotNull(scriptSupplier, "scriptSupplier not provided");
        if(!bearSettings.isConfigured()){
            bearSettings.configure(factory);
        }
        if(scriptSupplier != null){
            script = scriptSupplier.get();
            bearSettings.getGlobal().wire(script);
            script.setScriptsDir(null);
        }
        return this;
    }

    public CompositeTaskRunContext createRunContext()  {
        try {
            Preconditions.checkArgument(bearSettings.isConfigured(), "settings must be configured. call settings.init() to configure");
            init();
            script.prepareToRun();

            Stage stage = global.var(global.bear.getStage);
            return runContext  = stage.createRunContext();
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    /*public CompositeTaskRunContext run() throws Exception {
        runContext.submitTasks();

        if(shutdownAfterRun){
            if (await) {
                GlobalContext global = runContext.getGlobal();
                ConsolesDivider<SessionContext> consoleArrival = runContext.getConsoleArrival();

                consoleArrival.await(global.localCtx.var(global.bear.taskTimeoutSec));
            }

            global.shutdown();
        }

        return runContext;
    }*/


    public BearRunner shutdownAfterRun(boolean shutdownAfterRun) {
        this.shutdownAfterRun = shutdownAfterRun;
        return this;
    }
}
