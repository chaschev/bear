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

package cap4j.task;

import cap4j.core.Dependency;
import cap4j.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class InstallationTask<T extends TaskResult> extends Task<T>{
    public abstract Dependency asInstalledDependency();

    public InstallationTask() {
    }

    public InstallationTask(String name) {
        super(name);
    }

    @Override
    public InstallationTask<T> setCtx(SessionContext $) {
        super.setCtx($);
        return this;
    }

    private static final InstallationTask NOP_TASK = new InstallationTask("nop") {
        @Override
        public Dependency asInstalledDependency() {
            return Dependency.NONE;
        }
    };

    public static InstallationTask nop() {
        return NOP_TASK;
    }

}
