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

package bear.core;

import bear.plugins.Plugin;
import bear.session.SystemEnvironments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalContextFactory {
    private static final Logger logger = LoggerFactory.getLogger(GlobalContextFactory.class);

    public static GlobalContextFactory INSTANCE = new GlobalContextFactory();

    private final GlobalContext global = GlobalContext.getInstance();

    public GlobalContextFactory() {
        File file = global.localCtx.var(global.bear.globalPropertiesFile);

        if(file.exists()){
            global.loadProperties(file);
        }
    }

    public void init() {
        if (globalVarsInitPhase != null) {
            globalVarsInitPhase.setVars(global.variablesLayer);
        }

        if (userRegisteredPlugins != null) {
            for (Class<? extends Plugin> aClass : userRegisteredPlugins) {
                global.addPlugin(aClass);
            }

            global.initPlugins();
        }
    }

    public static interface GlobalVarsInitPhase {
        void setVars(VariablesLayer vars);
    }

    public GlobalVarsInitPhase globalVarsInitPhase;

    private List<Class<? extends Plugin>> userRegisteredPlugins = new ArrayList<Class<? extends Plugin>>();

    public GlobalContextFactory requirePlugins(Class<? extends Plugin>... plugins){
        Collections.addAll(userRegisteredPlugins, plugins);

        return this;
    }

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
