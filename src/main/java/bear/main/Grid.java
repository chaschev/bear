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
import bear.core.GlobalContext;
import bear.core.GridBuilder;
import bear.task.Tasks;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A dynamic script to run.
 *
 * Stage/roles/hosts selection: a script is allowed to set these, however they can overridden from outside.
 * Todo: freeze stage from the script.
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */

public abstract class Grid {
    protected static final Logger logger = LoggerFactory.getLogger(Grid.class);
    protected static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

    protected GlobalContext global;
    protected Bear bear;
    protected Tasks tasks;

    protected GridBuilder builder;
    protected GridBuilder b;

    public abstract void addPhases();


    public GridBuilder getBuilder() {
        return builder;
    }

    public void setBuilder(GridBuilder builder) {
        this.builder = b = builder;
    }
}
