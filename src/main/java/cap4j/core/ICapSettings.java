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

import java.io.InputStream;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class ICapSettings {
    protected GlobalContextFactory factory;
    protected GlobalContext global;

    protected ICapSettings(GlobalContextFactory factory) {
        this.factory = factory;
        global = factory.getGlobal();
    }

    public final GlobalContext configure(GlobalContextFactory factory) throws Exception {
        configureMe(factory);

        return global;
    }

    protected abstract GlobalContext configureMe(GlobalContextFactory factory) throws Exception;

    public ICapSettings loadProperties(InputStream is) throws Exception {
        global.loadProperties(is);

        return this;
    }

    public GlobalContextFactory getFactory() {
        return factory;
    }

    public GlobalContext getGlobal() {
        return global;
    }
}
