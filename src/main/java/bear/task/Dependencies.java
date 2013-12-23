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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Dependencies {
    protected List<Dependency> dependencies = new ArrayList<Dependency>();

    public Dependencies addDependencies(Dependency... dependencies) {
        Collections.addAll(this.dependencies, dependencies);
        return this;
    }

    public Dependencies addDependencies(Collection<Dependency> dependencies) {
        this.dependencies.addAll(dependencies);
        return this;
    }

    public DependencyResult check(){
        return Dependency.checkDeps(dependencies);
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public TaskResult tryInstall() {
        TaskResult result = TaskResult.OK;

        for (Dependency dependency : dependencies) {
            if(dependency.isInstallSupported()){
                result = dependency.install();

                if(!result.ok()) return result;
            }
        }

        return result;
    }
}
