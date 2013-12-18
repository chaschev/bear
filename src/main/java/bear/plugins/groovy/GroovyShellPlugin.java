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

package bear.plugins.groovy;

import bear.core.GlobalContext;
import bear.annotations.Shell;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.InstallationTask;
import bear.task.InstallationTaskDef;
import bear.task.Task;
import bear.task.TaskDef;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Shell("groovy")
public class GroovyShellPlugin extends Plugin {
    public final DynamicVariable<Boolean> sendToHosts = Variables.newVar(Boolean.TRUE);

    public GroovyShellPlugin(GlobalContext global) {
        super(global);
        this.shell = global.wire(new GroovyShellMode(this, cmdAnnotation()));
    }

    public GroovyShellPlugin(GlobalContext global, TaskDef<? extends Task> taskDef) {
        super(global, taskDef);
    }

    @Override
    public GroovyShellMode getShell() {
        return (GroovyShellMode) this.shell;
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }


}
