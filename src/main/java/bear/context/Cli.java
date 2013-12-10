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

import bear.core.*;
import bear.main.*;
import bear.plugins.groovy.GroovyShellMode;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.session.DynamicVariable;
import bear.session.Question;
import chaschev.io.FileUtils;
import chaschev.util.Exceptions;
import com.google.common.base.*;
import com.google.common.collect.Iterables;
import groovy.lang.GroovyClassLoader;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static bear.session.Variables.*;
import static com.google.common.collect.Lists.transform;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Cli extends AppCli<GlobalContext, Bear> {
    public static final Logger logger = LoggerFactory.getLogger(Cli.class);
    public static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

    public final DynamicVariable<String> settings = newVar(".bear/BearSettings.java");
    public final DynamicVariable<File> settingsFile = convert(settings, TO_FILE);
    public final DynamicVariable<File> script = undefined();

    public final DynamicVariable<Properties>
        newRunProperties = newVar(new Properties());

    protected GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    public BearFX bearFX;
    protected CompileManager compileManager;


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

    @Override
    public Cli configure() throws IOException {
        super.configure();

        compileManager = new CompileManager($(scriptsDir), $(buildDir));

        $.localCtx().log("configuring Bear with default settings...");


        global.getPlugin(GroovyShellPlugin.class).getShell().set$(this);

        build();

        return this;
    }

    protected Optional<CompiledEntry> findScriptToRun(List<CompiledEntry> compiledEntries) {
        if (isScriptNameSet()) {
            String scriptName = $(script).getName();

            BearMain.logger.info("script is set in the command line to {}", scriptName);
            bear.deployScript.defaultTo(scriptName);
        } else {
            new Question("Enter a script name to run:",
                transform(compiledEntries, new Function<CompiledEntry, String>() {
                    public String apply(CompiledEntry input) {
                        return input.aClass.getSimpleName();
                    }
                }),
                bear.deployScript).ask();
        }

        return Iterables.tryFind(compiledEntries, new Predicate<CompiledEntry>() {
            @Override
            public boolean apply(CompiledEntry input) {
                return input.aClass.getName().equals(global.var(bear.deployScript));
            }
        });
    }

    protected boolean isScriptNameSet() {
        return $.isSet(script) && $.var(script) != Fun.UNDEFINED;
    }

    public IBearSettings newSettings() {
        return newSettings($(settingsFile).getName());
    }

    public IBearSettings newSettings(String bearSettings) {
        try {
            final CompiledEntry settingsEntry = compileManager.findClass(bearSettings, false);

            Preconditions.checkNotNull(settingsEntry, "%s not found", bearSettings);

            IBearSettings settings = (IBearSettings) settingsEntry.newInstance(factory);

            settings.loadProperties($(newRunProperties));
            DependencyInjection.nameVars(settings, global);

            settings.configure(factory);

            return settings;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    protected Optional<CompiledEntry> compileAndLoadScript() throws MalformedURLException {
        CompilationResult result = compileManager.compileWithAll();

        return findScriptToRun((List) result.scriptClasses);
    }

    public void build() {
        compileManager.compileWithAll();
    }

    public static class CompilationResult {
        public long timestamp;

        public CompilationResult() {
        }

        public List<CompiledEntry> scriptClasses = new ArrayList<CompiledEntry>();
        public List<CompiledEntry> settingsClasses = new ArrayList<CompiledEntry>();

        public CompilationResult mergeIn() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * -Vcli.script=.bear/testCaptures.bear -Vcli.settings=.bear/SecureSocialDemoSettings.java -Vcli.propertiesFile=.bear/test.properties
     */
    public static void main(String[] args) throws Exception {
        Cli cli = new Cli(GlobalContext.getInstance(), args)
            .configure();

        IBearSettings settings = cli.newSettings();

        GlobalContext global = cli.global;
        File scriptFile = global.var(cli.script);

        Supplier<BearParserScriptSupplier.BearScriptParseResult> supplier;

        BearScriptRunner bearScriptRunner = new BearScriptRunner(global, null, settings);

        BearScriptRunner.RunResponse response;

        if(scriptFile.getName().endsWith(".groovy")){
            String script = FileUtils.readFileToString(scriptFile);
            if(GroovyShellMode.GRID_PATTERN.matcher(script).find()){
                GroovyClassLoader gcl = new GroovyClassLoader();
                Class clazz = gcl.parseClass(scriptFile);
                Grid grid = (Grid) clazz.newInstance();
                grid.setBuilder(new GridBuilder());
                global.wire(grid);

                grid.addPhases();

                response = bearScriptRunner.exec(grid.getBuilder(), true);
            }else{
                response = bearScriptRunner.exec(new GroovyScriptSupplier(global, scriptFile), true);
            }
        } else {
            supplier = new BearParserScriptSupplier(global.getPlugin(GroovyShellPlugin.class),
                FileUtils.readFileToString(scriptFile));

            response = bearScriptRunner.exec(supplier, true);
        }

        GlobalTaskRunner runner = response.getGlobalRunner();

        runner.getFinishedLatch().await();

        global.shutdown();
    }
}
