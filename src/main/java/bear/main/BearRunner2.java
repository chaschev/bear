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

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class BearRunner2 {
    private IBearSettings bearSettings;
    private GlobalContextFactory factory;
    private GlobalContext global;

    private boolean shutdownAfterRun;

    public BearRunner2(IBearSettings bearSettings,  GlobalContextFactory factory) {
        try {
            this.bearSettings = bearSettings;
            this.factory = factory;
            this.global = bearSettings.getGlobal();

        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }


    public PreparationResult createRunContext()  {
        try {
            Preconditions.checkArgument(bearSettings.isConfigured(), "settings must be configured. call settings.init() to configure");
            if(!bearSettings.isConfigured()){
                bearSettings.configure(factory);
            }

            Stage stage = global.var(global.bear.getStage);
            PreparationResult preparationResult = stage.prepareToRun2();
            return preparationResult;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

   /* public CompositeTaskRunContext run() throws Exception {
        runContext.submitTasks();

        if(shutdownAfterRun){
            boolean await = true;
            if (await) {
                GlobalContext global = runContext.getGlobal();
                ConsolesDivider<SessionContext> consoleArrival = runContext.getConsoleArrival();

                consoleArrival.await(global.localCtx.var(global.bear.taskTimeoutSec));
            }

            global.shutdown();
        }

        return runContext;
    }
*/

    public BearRunner2 shutdownAfterRun(boolean shutdownAfterRun) {
        this.shutdownAfterRun = shutdownAfterRun;
        return this;
    }
}
