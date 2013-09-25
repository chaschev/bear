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

package cap4j.core;

import cap4j.plugins.Plugin;
import cap4j.session.SystemEnvironments;
import com.chaschev.chutils.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalContextFactory {
    private static final Logger logger = LoggerFactory.getLogger(GlobalContextFactory.class);

    public static GlobalContextFactory INSTANCE = new GlobalContextFactory();

    private final GlobalContext global = GlobalContext.getInstance();

    public GlobalContextFactory() {
        global.loadProperties(global.localCtx.var(global.cap.settingsFile));
    }

    public void init() {
        if (globalVarsInitPhase != null) {
            globalVarsInitPhase.setVars(global.variablesLayer);
        }

        if (registerPluginsPhase != null) {
            final List<Class<? extends Plugin>> list = registerPluginsPhase.registerPlugins(global.variablesLayer);

            for (Class<? extends Plugin> aClass : list) {
                try {
                    final Plugin plugin = aClass.getConstructor(GlobalContext.class).newInstance(global);
                    Plugin.nameVars(plugin);
                    plugin.init();

                    global.pluginMap.put(aClass, plugin);
                } catch (Exception e) {
                    throw Exceptions.runtime(e);
                }
            }
        }
    }

    public static interface GlobalVarsInitPhase {
        void setVars(VariablesLayer vars);
    }

    public static interface RegisterPluginsPhase {
        List<Class<? extends Plugin>> registerPlugins(VariablesLayer vars);
    }

    public GlobalVarsInitPhase globalVarsInitPhase;
    public RegisterPluginsPhase registerPluginsPhase;

    public GlobalContext configure(SystemEnvironments system) {
        configure(global, system);
        return global;
    }

    protected GlobalContext configure(GlobalContext gc, SystemEnvironments system) {
        logger.warn("warning: global config was not configured");
        return gc;
    }

    public GlobalContext getGlobal() {
        return global;
    }
}
