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

import bear.context.AppCli;
import bear.context.DependencyInjection;
import bear.context.Fun;
import bear.main.BearFX;
import bear.main.CompileManager;
import bear.main.CompiledEntry;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.session.DynamicVariable;
import chaschev.util.Exceptions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Properties;

import static bear.session.Variables.*;
import static chaschev.lang.OpenBean.getFieldValue;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearMain extends AppCli<GlobalContext, Bear> {
    public static final Logger logger = LoggerFactory.getLogger(BearMain.class);
    public static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

    public final DynamicVariable<String> project = newVar(".bear/BearSettings.java");
    public final DynamicVariable<File> projectFile = convert(project, TO_FILE);
    public final DynamicVariable<String> projectClass = newVar("BearSettings");
    public final DynamicVariable<String> script = undefined();

    public final DynamicVariable<Properties>
        newRunProperties = newVar(new Properties());

    protected GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    public BearFX bearFX;
    protected CompileManager compileManager;


    public BearMain(GlobalContext global, String... args) {
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
    public BearMain configure() throws IOException {
        super.configure();

        compileManager = new CompileManager($(scriptsDir), $(buildDir));

        $.localCtx().log("configuring Bear with default settings...");

        global.plugin(GroovyShellPlugin.class).getShell().set$(this);

        build();

        return this;
    }

    protected Optional<CompiledEntry> findScriptToRun() {
        if (isScriptNameSet()) {
            String scriptName = $(script);

            logger.info("script is set in the command line to {}", scriptName);
            bear.deployScript.defaultTo(scriptName);
        } else {
//            new Question("Enter a script name to run:",
//                transform(compiledEntries, new Function<CompiledEntry, String>() {
//                    public String apply(CompiledEntry input) {
//                        return input.aClass.getSimpleName();
//                    }
//                }),
//                bear.deployScript).ask();
        }

        throw new UnsupportedOperationException("todo");
//        return Iterables.tryFind(compiledEntries, new Predicate<CompiledEntry>() {
//            @Override
//            public boolean apply(CompiledEntry input) {
//                return input.aClass.getName().equals(global.var(bear.deployScript));
//            }
//        });
    }

    protected boolean isScriptNameSet() {
        return $.isSet(script) && $.var(script) != Fun.UNDEFINED;
    }

    public BearProject newProject() {
        File file = $(projectFile);

        if(!compileManager.findClass(file).isPresent()){
            return newProject($(projectClass));
        }else{
            return newProject(file);
        }
    }

    public BearProject newProject(String className) {
        try {
            Optional<CompiledEntry> entry = compileManager.findClass(className);

            if(!entry.isPresent()){
                Preconditions.checkArgument(entry.isPresent(), "class %s not found", className);
            }

            return newProject(entry.get());
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public BearProject newProject(File bearProjectFile) {
        try {
            final Optional<CompiledEntry> settingsEntry = compileManager.findClass(bearProjectFile);

            Preconditions.checkArgument(settingsEntry.isPresent(), "%s not found", bearProjectFile);

            return newProject(settingsEntry.get());
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    protected BearProject newProject(CompiledEntry entry) throws Exception {
        BearProject settings = (BearProject) entry.newInstance(factory);

        settings.loadProperties($(newRunProperties));
        DependencyInjection.nameVars(settings, global);

        settings.configure(factory);

        return settings;
    }

    protected Optional<CompiledEntry> compileAndLoadScript() throws MalformedURLException {
//        JavaCompilationResult result = compileManager.compileWithAll();
        throw new UnsupportedOperationException("");
//        return findScriptToRun((List) result.scriptClasses);
    }

    public void build() {
        compileManager.compileWithAll();
    }

    /**
     * -VbearMain.appConfigDir=src/main/groovy/examples -VbearMain.buildDir=.bear/classes -VbearMain.script=dumpSampleGrid -VbearMain.projectClass=SecureSocialDemoProject -VbearMain.propertiesFile=.bear/test.properties
     */
    public static void main(String[] args) throws Exception {
        BearMain bearMain = new BearMain(GlobalContext.getInstance(), args)
            .configure();

        BearProject bearProject = bearMain.newProject();

        run(bearProject, true);

//        if(scriptFile.getName().endsWith(".groovy")){
//            String script = FileUtils.readFileToString(scriptFile);
//            if(GroovyShellMode.GRID_PATTERN.matcher(script).find()){
//                GroovyClassLoader gcl = new GroovyClassLoader();
//                Class clazz = gcl.parseClass(scriptFile);
//                Grid grid = (Grid) clazz.newInstance();
//                grid.setBuilder(new GridBuilder());
//                global.wire(grid);
//
//                grid.addPhases();
//
//                response = bearScriptRunner.exec(grid.getBuilder(), true);
//            }else{
//                response = bearScriptRunner.exec(new GroovyScriptSupplier(global, scriptFile), true);
//            }
//        }

//        else {
//
//            supplier = new BearParserScriptSupplier(global.getPlugin(GroovyShellPlugin.class),
//                FileUtils.readFileToString(scriptFile));
//
//            response = bearScriptRunner.exec(supplier, true);
//        }


    }

    public static void run(BearProject project, boolean shutdown){
        run(project, null, shutdown);
    }

    public static void run(BearProject project, @Nullable Map<Object, Object> variables, boolean shutdown){
        GlobalContext global = project.global;

        String script = global.var(project.main().script);

        Optional<GridBuilder> gridOptional = getFieldValue(project, script, GridBuilder.class);

        if(!gridOptional.isPresent()){
            throw new IllegalArgumentException("did not find field: " + script);
        }

        run(project, gridOptional.get(), variables, shutdown);
    }

    public static void run(BearProject project, GridBuilder grid, Map<Object, Object> variables, boolean shutdown){
        try {
            GlobalContext global = project.global;

            BearScriptRunner bearScriptRunner = new BearScriptRunner(global, null, project).withVars(variables);

            BearScriptRunner.RunResponse response = bearScriptRunner.exec(grid, true);

            try {
                GlobalTaskRunner runner = response.getGlobalRunner();

                runner.getFinishedLatch().await();
            } finally {
                if(response.getSavedVariables() != null){
                    global.putMap(response.getSavedVariables());
                }
            }

            if(shutdown){
                shutdown(global);
            }
        } catch (InterruptedException e) {
            throw Exceptions.runtime(e);
        }

    }

    public static void shutdown(GlobalContext global) throws InterruptedException {
        global.shutdown();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);

                    File file = new File("threads.txt");

                    System.out.println("some threads are still running, see " + file.getAbsolutePath() +
                        " for details");

                    StringBuilder sb = threadDump();

                    org.apache.commons.io.FileUtils.writeStringToFile(file, sb.toString());

                    System.exit(0);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        }, "shutdown monitor");
    }

    public static StringBuilder threadDump() {
        Map<Thread,StackTraceElement[]> map = Thread.getAllStackTraces();

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Thread, StackTraceElement[]> e : map.entrySet()) {
            Thread thread = e.getKey();

            StackTraceElement[] elements = e.getValue();
            StackTraceElement el = elements == null || elements.length == 0 ? null : elements[0];

            sb.append(thread.getName());
            if(el != null){
                sb.append("\tat ").append(el)
                .append("\n");
            }
        }

        sb.append("\n\n");

        Exception e = new Exception();

        for (Map.Entry<Thread, StackTraceElement[]> entry : map.entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stack = entry.getValue();
            sb.append(thread.getName()).append(", id=").append(thread.getId()).append("\n");
            e.setStackTrace(stack);
            sb.append(Throwables.getStackTraceAsString(e));
            sb.append("\n");
        }
        return sb;
    }
}
