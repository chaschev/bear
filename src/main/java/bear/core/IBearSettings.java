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

import com.chaschev.chutils.util.Exceptions;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class IBearSettings {
    protected GlobalContextFactory factory;
    protected GlobalContext global;

    protected IBearSettings(GlobalContextFactory factory) {
        this.factory = factory;
        global = factory.getGlobal();
    }

    public IBearSettings(GlobalContextFactory factory, @Nullable String resource) {
        this(factory);

        if (resource != null) {
            try {
                loadProperties(getClass().getResourceAsStream(resource));
            } catch (Exception e) {
                throw Exceptions.runtime(e);
            }
        }
    }

    public IBearSettings(GlobalContextFactory factory, @Nullable File file) {
        this(factory);

        if (file != null) {
            loadProperties(file);
        }
    }

    public final GlobalContext configure(GlobalContextFactory factory) throws Exception {
        configureMe(factory);

        return global;
    }

    protected abstract GlobalContext configureMe(GlobalContextFactory factory) throws Exception;

    public IBearSettings loadProperties(InputStream is) throws Exception {
        global.loadProperties(is);

        return this;
    }

    public IBearSettings loadProperties(File file) {
        try {
            final FileInputStream fis = new FileInputStream(file);
            loadProperties(fis);
            return this;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }

    }


    public GlobalContextFactory getFactory() {
        return factory;
    }

    public GlobalContext getGlobal() {
        return global;
    }
}
