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
    private BearProject bearProject;
    private GlobalContextFactory factory;
    private GlobalContext global;

    private boolean shutdownAfterRun;

    public BearRunner2(BearProject bearProject,  GlobalContextFactory factory) {
        try {
            this.bearProject = bearProject;
            this.factory = factory;
            this.global = bearProject.getGlobal();
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }


    public PreparationResult createRunContext()  {
        try {
            Preconditions.checkArgument(bearProject.isConfigured(), "settings must be configured. call settings.init() to configure");
            if(!bearProject.isConfigured()){
                bearProject.configure(factory);
            }

            Stage stage = global.var(global.bear.getStage);

            return stage.prepareToRun2(bearProject);
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public BearRunner2 shutdownAfterRun(boolean shutdownAfterRun) {
        this.shutdownAfterRun = shutdownAfterRun;
        return this;
    }
}
