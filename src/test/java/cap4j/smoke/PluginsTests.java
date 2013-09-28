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

package cap4j.smoke;

import atocha.Atocha;
import cap4j.core.GlobalContextFactory;
import cap4j.main.Cap4j;
import cap4j.main.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PluginsTests {
    public static final Logger logger = LoggerFactory.getLogger(PluginsTests.class);

    public static class SetupPluginsScript extends Script {
        @Override
        public void run() throws Exception {
            cap.stage.defaultTo("vm02");
            cap.autoInstallPlugins.defaultTo(true);
            cap.task.defaultTo("setup");

            global.run();
            global.shutdown();
        }

        public static void main(String[] args) throws Exception {
            new Cap4j.Cap4jRunner(new SetupPluginsSettings(GlobalContextFactory.INSTANCE).loadProperties(
                PluginsTests.class.getResourceAsStream("/test.properties")
            ), new SetupPluginsScript()).run();
        }
    }

    public static class GithubGrailsAppScript extends Script {
        @Override
        public void run() throws Exception {
            cap.stage.defaultTo("vm02");
            cap.task.defaultTo("deploy");
            cap.clean.defaultTo(false, true);

            global.getPlugin(Atocha.class).reuseWar.defaultTo(true, true);

            cap.vcsBranchName.defaultTo("master");

            global.run();
            global.shutdown();
        }

        public static void main(String[] args) throws Exception {
            new Cap4j.Cap4jRunner(new SetupPluginsSettings(GlobalContextFactory.INSTANCE).loadProperties(
                PluginsTests.class.getResourceAsStream("/test.properties")
            ), new GithubGrailsAppScript()).run();
        }
    }
}
