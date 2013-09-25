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

package atocha;

import cap4j.core.Cap;
import cap4j.core.GlobalContext;
import cap4j.plugins.Plugin;
import cap4j.session.DynamicVariable;
import cap4j.task.Task;

/**
* User: achaschev
* Date: 8/13/13
* Time: 8:02 PM
*/
public class Atocha extends Plugin {

    public final DynamicVariable<Boolean>
        reuseWar = Cap.bool("will skip building WAR").defaultTo(false);

    public Atocha(GlobalContext global) {
        super(global);
    }

    @Override
    public Task getSetup() {
        return null;
    }
}
