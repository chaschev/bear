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

package bear.plugins;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.Stage;
import bear.plugins.groovy.Replacements;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class PluginShellMode<T extends Plugin> implements CommandInterpreter {
    protected final T plugin;
    protected String commandName;
    protected String description;

    protected GlobalContext global;
    protected Bear bear;

    protected PluginShellMode(T plugin) {
        this(plugin, plugin.cmdAnnotation());
    }

    protected PluginShellMode(T plugin, String commandName) {
        this.plugin = plugin;
        this.commandName = commandName;
        this.global = plugin.getGlobal();
        this.bear = global.bear;
    }

    public String getCommandName() {
        return commandName;
    }

    @Override
    public String toString() {
        return commandName;
    }

    @Override
    public Stage getStage() {
        return null;
    }

    /**
     * Called during the init phase of a plugin.
     */
    public void init(){

    }

    public T getPlugin() {
        return plugin;
    }

    @Override
    public Replacements completeCode(String script, int position) {
        return Replacements.EMPTY;
    }

    /**
     * JS, Groovy, Java are multi-line. Sh is not.
     * @return
     */
    public boolean multiLine(){
        return true;
    }
}
