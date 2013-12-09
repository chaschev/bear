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

package bear.task;

import bear.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class InstallationTaskDef<TASK extends InstallationTask> extends TaskDef<TASK>{
    public static final InstallationTaskDef<InstallationTask> EMPTY = new InstallationTaskDef<InstallationTask>(new SingleTaskSupplier() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return InstallationTask.nop();
        }
    });

    public InstallationTaskDef(SingleTaskSupplier singleTaskSupplier) {
        super(singleTaskSupplier);
    }
}
