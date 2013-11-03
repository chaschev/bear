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

import bear.core.Bear;
import bear.core.CompositeTaskRunContext;
import bear.core.GlobalContext;
import bear.core.SessionContext;

import java.io.File;

/**
 * A dynamic script to run.
 *
 * Stage/roles/hosts selection: a script is allowed to set these, however they can overridden from outside.
 * Todo: freeze stage from the script.
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Script {
    public File scriptsDir;

    public GlobalContext global;
    public Bear bear;
    public final String id = SessionContext.randomId();

    protected abstract void configure() throws Exception;

    public Script setProperties(GlobalContext global, File scriptsDir) {
        this.global = global;
        this.scriptsDir = scriptsDir;
        bear = global.bear;
        return this;
    }

    public CompositeTaskRunContext prepareToRun() throws Exception {
        configure();

        return global.prepareToRun();
    }
}
