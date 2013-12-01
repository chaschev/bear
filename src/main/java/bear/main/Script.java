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

import bear.context.Var;
import bear.core.*;
import bear.task.SessionTaskRunner;
import bear.task.Task;
import bear.task.TaskResult;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * A dynamic script to run.
 *
 * Stage/roles/hosts selection: a script is allowed to set these, however they can overridden from outside.
 * Todo: freeze stage from the script.
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */

public class Script {
    protected static final Logger logger = LoggerFactory.getLogger(Script.class);
    protected static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

    @Var("bear.scriptsDir")
    public File scriptsDir;

    public GlobalContext global;
    public Bear bear;
    public SessionTaskRunner runner;

    @Var(skipWiring = true)
    public final String id = SessionContext.randomId();

    protected Task parent;
    public Task task;

    public void configure() throws Exception {};

    public Script setScriptsDir(File scriptsDir) {
        this.scriptsDir = scriptsDir;
        return this;
    }

    public void prepareToRun() throws Exception {
        configure();
    }

    public TaskResult run(){
        return TaskResult.OK;
    }

    public void setParent(Task parent) {
        this.parent = parent;
    }
}
