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
import bear.context.AppOptions;
import bear.context.DependencyInjection;
import bear.context.Fun;
import bear.core.except.NoSuchFileException;
import bear.main.*;
import bear.maven.LoggingBooter;
import bear.maven.MavenBooter;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.session.DynamicVariable;
import chaschev.lang.OpenBean;
import chaschev.util.Exceptions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import groovy.lang.GroovyShell;
import joptsimple.OptionSpec;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

import static bear.session.Variables.*;
import static chaschev.lang.OpenBean.getFieldValue;
import static java.util.Collections.singletonList;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearMain extends AppCli<GlobalContext, Bear, BearMain.AppOptions2> {
    public static final Logger logger = LoggerFactory.getLogger(BearMain.class);
    public static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");
    public static final File BEAR_DIR = new File(".bear");
    public static final File BUILD_DIR = new File(BEAR_DIR, "classes");

    protected GlobalTaskRunner lastRunner;
    protected boolean fxApp;

    static {
        if (!BUILD_DIR.exists()) BUILD_DIR.mkdirs();
    }

    public static class AppOptions2 extends AppOptions {
        public final static OptionSpec<String>
            CREATE_NEW = parser.accepts("create", "creates a new project, i.e. 'my-project'").withRequiredArg().describedAs("name-with-dashes").ofType(String.class),
            USER = parser.accepts("user", "remote user for the deployment").requiredIf(CREATE_NEW).withRequiredArg().describedAs("username").ofType(String.class),
            PASSWORD = parser.accepts("password", "user password").requiredIf(CREATE_NEW).withRequiredArg().describedAs("password").ofType(String.class),
            HOST = parser.accepts("host", "any of the remote hosts").requiredIf(CREATE_NEW).withRequiredArg().describedAs("host").ofType(String.class)
        ;

        public final static OptionSpec<Void>
            USE_UI = parser.accepts("ui", "start UI (override project default)"),
            NO_UI = parser.accepts("no-ui", "don't run UI (override project default)"),
            QUIET = parser.accepts("q", "be quiet"),
            UNPACK_DEMOS = parser.accepts("unpack-demos", "unpack the demos");

        public AppOptions2(String[] args) {
            super(args);
        }
    }

    public final DynamicVariable<String>
        project = newVar(".bear/BearSettings.java"),
        projectClass = newVar("BearSettings"),
        script = undefined(),
        customFolders = undefined("CustomFolders to search for the projects");

    public final DynamicVariable<File> projectFile = convert(project, TO_FILE);

    public final DynamicVariable<Properties>
        newRunProperties = newVar(new Properties());

    protected GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    public BearFX bearFX;
    protected CompileManager compileManager;

    public BearMain(GlobalContext global, CompileManager compileManager, String... args) {
        super(global, args);

        if (compileManager != null) {
            this.compileManager = compileManager;
        }

        DependencyInjection.nameVars(this, $);
    }

    public Bear getBear() {
        return bear;
    }

    public GlobalContextFactory getFactory() {
        return factory;
    }

    @Override
    protected AppOptions2 createOptions(String... args) {
        return new AppOptions2(args);
    }

    @Override
    public BearMain configure() throws IOException {
        Preconditions.checkState(compileManager != null, "compiler must be injected at this point");

        super.configure();

//        if(!fxApp){
          configureLoggersForConsole();
//        }

//        global.loadProperties(new File(global.var(appConfigDir), "bootstrap.properties"));

        logger.info("configuring Bear with default settings...");

        global.plugin(GroovyShellPlugin.class).getShell().set$(this);

        build();

        return this;
    }

    protected void configureLoggersForConsole() {
        Appender fxAppDebug =
            FileAppender.createAppender(
                ".bear/logs/ui-cli-debug.log",
                null,
                null,
                "cliDebug",
                null,
                null,
                null,
                PatternLayout.createLayout("%d{HH:mm:ss.S} %c{1.} - %msg%n", null, null, null, null),
                ThresholdRangeFilter.createFilter("DEBUG", "INFO", null, null),
                null,
                null,
                null
            );

        Appender fxAppInfo =
            FileAppender.createAppender(
                ".bear/logs/ui-cli.log",
                null,
                null,
                "cliInfo",
                null,
                null,
                null,
                PatternLayout.createLayout("%d{HH:mm:ss.S} %c{1.} - %msg%n", null, null, null, null),
                ThresholdRangeFilter.createFilter("INFO", "OFF", null, null),
                null,
                null,
                null
            );

        fxAppDebug.start();
        fxAppInfo.start();

        LoggingBooter.addLog4jAppender(LogManager.getRootLogger(), fxAppInfo, null, null, false);
        LoggingBooter.addLog4jAppender("fx", fxAppDebug, null, null, false);

        LoggingBooter.loggerDiagnostics();
    }

    static final CompileManager compiler = newCompileManager();

    public static synchronized CompileManager getCompilerManager(){
        return compiler;
    }

    private static CompileManager newCompileManager() {
        List<File> folders = Lists.newArrayList(BEAR_DIR);

        Optional<ClassLoader> dependenciesCL = createDepsClassLoader(folders);

        CompileManager manager = new CompileManager(folders, BUILD_DIR);

        manager.setDependenciesCL(dependenciesCL);

        return manager;
    }

    static Optional<ClassLoader> createDepsClassLoader(List<File> folders) {
        File bootstrapFile = new File(BEAR_DIR, "bootstrap.properties");

        if (!bootstrapFile.exists()) return Optional.absent();

        FileInputStream fis = null;

        try {
            Properties properties = new Properties();

            fis = new FileInputStream(bootstrapFile);

            properties.load(fis);

            Set<Map.Entry<String, String>> entries = (Set) properties.entrySet();

            for (Map.Entry<String, String> entry : entries) {
                String key = entry.getKey();

                if (!key.startsWith("logger.")) continue;

                key = StringUtils.substringAfter(key, "logger.");

                LoggingBooter.changeLogLevel(key, Level.toLevel(entry.getValue()));
            }

            String property = "bearMain.customFolders";

            String customFolders = properties.getProperty(property, null);

            if (customFolders != null) {
                List<String> temp = COMMA_SPLITTER.splitToList(customFolders);

                for (String s : temp) {
                    File file = new File(s);

                    if (!file.exists()) {
                        throw new NoSuchFileException("dir does not exist (:" + property + "): " + s);
                    }

                    folders.add(file);
                }
            }

            return new MavenBooter(properties).loadArtifacts(properties);
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    private List<File> getScriptFolders2() {
        List<File> folders = singletonList($(scriptsDir));

        if ($.isSet(customFolders)) {
            List<String> temp = COMMA_SPLITTER.splitToList($(customFolders));

            folders = new ArrayList<File>(temp.size());
            folders.add($(scriptsDir));

            for (String s : temp) {

                File file = new File(s);

                if (!file.exists()) {
                    throw new NoSuchFileException("dir does not exist (:" + customFolders.name() + "): " + s);
                }

                folders.add(file);
            }
        }
        return folders;
    }

    protected boolean isScriptNameSet() {
        return $.isSet(script) && $.var(script) != Fun.UNDEFINED;
    }

    public BearProject newProject() {
        File file = $(projectFile);

        if (!compileManager.findClass(file).isPresent()) {
            return newProject($(projectClass));
        } else {
            return newProject(file);
        }
    }

    public BearProject newProject(String className) {
        try {
            Optional<CompiledEntry<?>> entry = compileManager.findClass(className);

            if (!entry.isPresent()) {
                Preconditions.checkArgument(entry.isPresent(), "class %s not found", className);
            }

            return newProject(entry.get());
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public BearProject newProject(File bearProjectFile) {
        try {
            final Optional<CompiledEntry<?>> settingsEntry = findEntry(bearProjectFile);

            return newProject(settingsEntry.get());
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    protected Optional<CompiledEntry<?>> findEntry(File bearProjectFile) {
        final Optional<CompiledEntry<?>> settingsEntry = compileManager.findClass(bearProjectFile);

        Preconditions.checkArgument(settingsEntry.isPresent(), "%s not found", bearProjectFile);

        return settingsEntry;
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
        int i = ArrayUtils.indexOf(args, "--log-level");

        if (i != -1) {
            LoggingBooter.changeLogLevel(LogManager.ROOT_LOGGER_NAME, Level.toLevel(args[i + 1]));
        }

        i = ArrayUtils.indexOf(args, "-q");

        if (i != -1) {
            LoggingBooter.changeLogLevel(LogManager.ROOT_LOGGER_NAME, Level.WARN);
        }

        GlobalContext global = GlobalContext.getInstance();

        BearMain bearMain = null;

        try {
            bearMain = new BearMain(global, getCompilerManager(), args);
        } catch (Exception e) {
            if(e.getClass().getSimpleName().equals("MissingRequiredOptionException")){
                System.out.println(e.getMessage());
            }else{
                Throwables.getRootCause(e).printStackTrace();
            }

            System.exit(-1);
        }

        if (bearMain.checkHelpAndVersion()) {
            return;
        }

        if(bearMain.options.has(AppOptions2.UNPACK_DEMOS)){
            String filesAsText = ProjectGenerator.readResource("/demoFiles.txt");

            int count = 0;

            for (String resource : filesAsText.split("::")) {
                File dest = new File(BEAR_DIR + resource);
                System.out.printf("copying %s to %s...%n", resource, dest);
                FileUtils.writeStringToFile(dest, ProjectGenerator.readResource(resource));

                count++;
            }

            System.out.printf("extracted %d files%n", count);

            return;
        }

        if (bearMain.options.has(AppOptions2.CREATE_NEW)) {
            String dashedTitle = bearMain.options.get(AppOptions2.CREATE_NEW);
            String user = bearMain.options.get(AppOptions2.USER);
            String pass = bearMain.options.get(AppOptions2.PASSWORD);
            String host = bearMain.options.get(AppOptions2.HOST);

            ProjectGenerator g = new ProjectGenerator(dashedTitle, user, pass, host);

            File projectFile = new File(BEAR_DIR, g.getProjectTitle() + ".groovy");
            File pomFile = new File(BEAR_DIR, "pom.xml");

            FileUtils.writeStringToFile(projectFile, g.processTemplate("TemplateProject.template"));
            FileUtils.writeStringToFile(new File(BEAR_DIR, dashedTitle + ".properties"), g.processTemplate("project-properties.template"));
            FileUtils.writeStringToFile(new File(BEAR_DIR, "demos.properties"), g.processTemplate("project-properties.template"));
            FileUtils.writeStringToFile(new File(BEAR_DIR, "bear-fx.properties"), g.processTemplate("bear-fx.properties.template"));
            FileUtils.writeStringToFile(pomFile, g.generatePom(dashedTitle));

            System.out.printf("Created project file: %s%n", projectFile.getPath());
            System.out.printf("Created maven pom: %s%n", pomFile.getPath());

            System.out.println("\nProject files have been created. You may open a Bear project in your favourite IDE by importing a Maven module (.bear/pom.xml).");

            return;
        }

        Bear bear = global.bear;

        if(bearMain.options.has(AppOptions2.QUIET)){
            global.put(bear.quiet, true);
            LoggingBooter.changeLogLevel(LogManager.ROOT_LOGGER_NAME, Level.WARN);
        }

        if(bearMain.options.has(AppOptions2.USE_UI)){
            global.put(bear.useUI, true);
        }

        if(bearMain.options.has(AppOptions2.NO_UI)){
            global.put(bear.useUI, false);
        }

        List<?> list = bearMain.options.getOptionSet().nonOptionArguments();

        if (list.size() > 1) {
            throw new IllegalArgumentException("too many arguments: " + list + ", " +
                "please specify an invoke line, project.method(arg1, arg2)");
        }

        if (list.isEmpty()) {
            throw new UnsupportedOperationException("todo implement running a single project");
        }

        String invokeLine = (String) list.get(0);

        String projectName;
        String method;

        if (invokeLine.contains(".")) {
            projectName = StringUtils.substringBefore(invokeLine, ".");
            method = StringUtils.substringAfter(invokeLine, ".");
        } else {
            projectName = invokeLine;
            method = null;
        }

        if (method == null || method.isEmpty()) method = "deploy()";
        if (!method.contains("(")) method += "()";

        Optional<CompiledEntry<? extends BearProject>> optional = bearMain.compileManager.findProject(projectName);

        if (!optional.isPresent()) {
            throw new IllegalArgumentException("project was not found: " + projectName +
                ", loaded classes: " + bearMain.compileManager.findProjects() +
                ", searched in: " + bearMain.compileManager.getSourceDirs() + ", ");
        }

        BearProject project = OpenBean.newInstance(optional.get().aClass)
            .injectMain(bearMain);

        GroovyShell shell = new GroovyShell();

        shell.setVariable("project", project);
        shell.evaluate("project." + method);
    }

    public GlobalTaskRunner run(BearProject project, boolean shutdown) {
        return run(project, null, shutdown, false);
    }

    public GlobalTaskRunner run(BearProject project, @Nullable Map<Object, Object> variables, boolean shutdown) {
        return run(project, variables, shutdown, false);
    }

    public GlobalTaskRunner run(BearProject project, @Nullable Map<Object, Object> variables, boolean shutdown, boolean async) {
        GlobalContext global = project.global;

        String script = global.var(project.main().script);

        Optional<GridBuilder> gridOptional = getFieldValue(project, script, GridBuilder.class);

        if (!gridOptional.isPresent()) {
            throw new IllegalArgumentException("did not find field: " + script);
        }

        return run(project, gridOptional.get(), variables, shutdown, async);
    }

    public GlobalTaskRunner run(BearProject project, GridBuilder grid, Map<Object, Object> variables, boolean shutdown) {
        return run(project, grid, variables, shutdown, true);
    }

    public GlobalTaskRunner run(BearProject project, GridBuilder grid, Map<Object, Object> variables, boolean shutdown, boolean async) {
        try {
            GlobalContext global = project.global;

            BearScriptRunner bearScriptRunner = new BearScriptRunner(global, null, project).withVars(variables);

            BearScriptRunner.RunResponse response = bearScriptRunner.exec(grid, true);

            try {
                GlobalTaskRunner runner = response.getGlobalRunner();

                if(!async){
                    runner.getFinishedLatch().await();

                    System.out.println("finished: " + runner.stats.getDefaultValue().toString());
                }

                return lastRunner = runner;
            } finally {
                try {
                    if (shutdown) {
                        shutdown(global);
                    }
                } catch (Exception e) {
                    logger.warn("exception during shutdown", e);
                }

                if (!async && response.getSavedVariables() != null) {
                    global.putMap(response.getSavedVariables());
                }
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
        Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Thread, StackTraceElement[]> e : map.entrySet()) {
            Thread thread = e.getKey();

            StackTraceElement[] elements = e.getValue();
            StackTraceElement el = elements == null || elements.length == 0 ? null : elements[0];

            sb.append(thread.getName());
            if (el != null) {
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
