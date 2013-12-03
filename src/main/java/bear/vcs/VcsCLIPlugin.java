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

package bear.vcs;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.session.Variables;
import bear.task.Task;
import bear.task.TaskDef;

import java.util.Collections;
import java.util.Map;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class VcsCLIPlugin<TASK extends Task, VCS_TASK_DEF extends TaskDef<? extends Task>> extends Plugin<TASK, VCS_TASK_DEF> {

    protected VcsCLIPlugin(GlobalContext global, VCS_TASK_DEF taskDef) {
        super(global, taskDef);
    }

    @Override
    public void initPlugin() {
        String msg = Variables.checkSet(global.localCtx, this.getClass().getSimpleName(),
            bear.repositoryURI
//            cap.vcsBranchName - no, because it's set in the script
        );

        if(msg != null){
            global.localCtx.log("%s", msg);
            throw new RuntimeException(msg);
        }
    }

    public static Map<String, String> emptyParams() {
        return Collections.emptyMap();
    }

    public abstract VCSSession newSession(SessionContext $, Task<TaskDef> parent);

    public static class StringResult extends CommandLineResult {
        public String value;

        public StringResult(String text, String value) {
            super(text);

            this.value = value;
        }
    }

}
