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

package bear.context;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.GlobalContextFactory;
import bear.main.BearFX;
import bear.session.DynamicVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Cli extends AppCli<GlobalContext, Bear> {
    public static final Logger logger = LoggerFactory.getLogger(Cli.class);

    public final DynamicVariable<File> settingsFile = convert(concat(appConfigDir, "/BearSettings.java"), TO_FILE);
    public final DynamicVariable<File> script = undefined();

    public final DynamicVariable<Properties>
        newRunProperties = newVar(new Properties());

    protected GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    public BearFX bearFX;

    public Cli(GlobalContext global, String... args) {
        super(global, args);

        DependencyInjection.nameVars(this, $);
    }

    public Bear getBear() {
        return bear;
    }

    public GlobalContextFactory getFactory() {
        return factory;
    }

}
