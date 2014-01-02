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

import bear.annotations.Configuration;
import bear.annotations.Project;
import bear.annotations.Variable;
import bear.plugins.DeploymentPlugin;
import bear.plugins.Plugin;
import bear.plugins.PluginShellMode;
import bear.plugins.ServerToolPlugin;
import bear.plugins.misc.ReleasesPlugin;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.session.Address;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.task.*;
import chaschev.lang.OpenBean;
import chaschev.lang.reflect.Annotations;
import chaschev.lang.reflect.MethodDesc;
import chaschev.util.Exceptions;
import com.google.common.base.*;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.logging.log4j.core.helpers.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Optional.fromNullable;
import static java.util.Collections.singletonList;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class BearProject<SELF extends BearProject> {
    private static final Logger logger = LoggerFactory.getLogger(BearProject.class);

    /**
     * Typically you would want to turn it off when you do several calls one of which redefines configuration.
     */
    protected boolean useAnnotations = true;
    protected GlobalContextFactory factory;
    protected GlobalContext global;
    private boolean configured;
    private Map<Object, Object> variables;
    private BearMain bearMain;
    protected Bear bear;

    protected boolean shutdownAfterRun = true;

    protected DeploymentPlugin.Builder defaultDeployment;

    protected ReleasesPlugin releases;
    private boolean unblockUninstall;
    private Object input;
    private boolean async;

    protected BearProject() {
        this(GlobalContextFactory.INSTANCE);
        global = factory.getGlobal();
        bear = global.bear;
    }

    protected BearProject(GlobalContextFactory factory) {
        this.factory = factory;
        global = factory.getGlobal();
        bear = global.bear;
    }

    public BearProject(GlobalContextFactory factory, @Nullable String propsResourceString) {
        this(factory);

        if (propsResourceString != null) {
            try {
                loadProperties(getClass().getResourceAsStream(propsResourceString));
            } catch (Exception e) {
                throw Exceptions.runtime(e);
            }
        }
    }

    public BearProject withMap(Map<Object, Object> variables) {
        this.variables = Collections.unmodifiableMap(variables);
        return this;
    }

    public BearProject(GlobalContextFactory factory, @Nullable File file) {
        this(factory);

        if (file != null) {
            loadProperties(file);
        }
    }

    public final SELF configure() {
        try {
            return configure(factory);
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public final SELF configure(GlobalContextFactory factory) throws Exception {
        main().configure();

        Preconditions.checkArgument(!configured, "already configured");

        for (Field field : OpenBean.fieldsOfType(this.getClass(), Plugin.class)) {
            Class<? extends Plugin<TaskDef>> aClass = (Class<? extends Plugin<TaskDef>>) field.getType();

            factory.requirePlugins(aClass);
        }

        factory.initPluginsAndWire(this);

        Iterable<Field> fields = OpenBean.fieldsOfType(this, GridBuilder.class);

        for (Field field : fields) {
            ((GridBuilder) field.get(this)).init(this);
        }

//        List<MethodDesc<? extends GridBuilder>> list = OpenBean.methodsReturning(this.getClass(), GridBuilder.class);

//        iter

        configureMe(factory);

        configured = true;

        return self();
    }

    protected abstract GlobalContext configureMe(GlobalContextFactory factory) throws Exception;

    public BearProject loadProperties(InputStream is) throws Exception {
        global.loadProperties(is);

        return this;
    }

    public BearProject loadProperties(Properties props) {
        global.loadProperties(props);
        return this;
    }

    public BearProject loadProperties(File file) {
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

    public boolean isConfigured() {
        return configured;
    }

//    public void run() {
//        BearMain.run(this, variables, true);
//    }

    public <T> SELF set(DynamicVariable<T> var, T value) {
        if (!global.isSet(var)) {
            global.putConst(var, value);
        }

        return self();
    }

    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF) this;
    }

    public SELF injectMain(BearMain bearMain) {
        this.bearMain = bearMain;
        return self();
    }

    public synchronized BearMain main() {
        if (bearMain == null) {
            bearMain = new BearMain(global, BearMain.getCompilerManager());
        }

        return bearMain;
    }

    public GridBuilder newGrid() {
        GridBuilder gb = new GridBuilder();

        gb.project = this;
        gb.bearMain = main();

        return gb;
    }

    public TaskDef<Object, TaskResult> newDeployTask() {
        checkDeployment();
        return defaultDeployment.build();
    }

    protected List<TaskDef<Object, TaskResult>> startServiceTaskDefs() {
        checkDeployment();

        return defaultDeployment.getStartService().createTasksToList(new ArrayList<TaskDef<Object, TaskResult>>());
    }

    protected List<TaskDef<Object, TaskResult>> stopServiceTaskDefs() {
        checkDeployment();

        return defaultDeployment.getStopService().createTasksToList(new ArrayList<TaskDef<Object, TaskResult>>());
    }

    public GlobalTaskRunner start() {
        return runTasksWithAnnotations(new Supplier<List<TaskDef<Object, TaskResult>>>() {
            @Override
            public List<TaskDef<Object, TaskResult>> get() {
                return startServiceTaskDefs();
            }
        }, useAnnotations);
    }

    public GlobalTaskRunner stop() {
        return runTasksWithAnnotations(new Supplier<List<TaskDef<Object, TaskResult>>>() {
            @Override
            public List<TaskDef<Object, TaskResult>> get() {
                return stopServiceTaskDefs();
            }
        }, useAnnotations);
    }

    public GlobalTaskRunner deploy() {
        return runTasksWithAnnotations(new Supplier<List<TaskDef<Object, TaskResult>>>() {
            @Override
            public List<TaskDef<Object, TaskResult>> get() {
                return singletonList(defaultDeployment.build());
            }
        }, useAnnotations);
    }

    public GlobalTaskRunner setup() {
        return setup(true);
    }

    public GlobalTaskRunner setup(boolean autoInstall) {
        if (autoInstall) {
            set(bear.verifyPlugins, true);
            set(bear.autoInstallPlugins, true);
            set(bear.checkDependencies, true);
        }

        input = this;

        return runTasksWithAnnotations(new Supplier<List<InstallationTaskDef<InstallationTask>>>() {
            @Override
            public List<InstallationTaskDef<InstallationTask>> get() {
                return singletonList(global.tasks.setup);
            }
        });
    }

    public void rollbackTo(final String ref) {
        runTasksWithAnnotations(new Supplier<List<TaskDef<Object, TaskResult>>>() {
            @Override
            public List<TaskDef<Object, TaskResult>> get() {
                return singletonList(rollbackToTask(ref));
            }
        });
    }

    public void invoke() {
        String method = getClass().getAnnotation(Project.class).method();

        Preconditions.checkArgument(!method.isEmpty(), "method() must not be empty for @Project when not providing a invoking a project");

        invoke(method);
    }

    public void invoke(String method, Object... params) {
        Preconditions.checkNotNull(method, "method must not be null");

        setProjectVars();

        MethodDesc methodDesc = OpenBean.getClassDesc(getClass()).getMethodDesc(method, false, params);

        Configuration projectAnnotation = projectConf();
        Configuration methodAnnotation = methodDesc.getMethod().getAnnotation(Configuration.class);

        configureWithAnnotations(methodAnnotation, projectAnnotation);

        global.put(bear.useUI, useUI(firstNonNull(methodAnnotation, projectAnnotation)));

        Object result = methodDesc.invoke(this, params);

        System.out.println("returned result: " + result);
    }

    public GlobalTaskRunner run(final List<? extends TaskCallable> callables) {
        return run(callables, useAnnotations);
    }

    public GlobalTaskRunner run(final List<? extends TaskCallable> callables, boolean useAnnotations) {
        return runTasksWithAnnotations(new Supplier<List<? extends TaskDef>>() {
            @Override
            public List<? extends TaskDef> get() {
                return Lists.newArrayList(Lists.transform(callables, new Function<TaskCallable, TaskDef>() {
                    @Override
                    public TaskDef apply(TaskCallable input) {
                        return new TaskDef(input);
                    }
                }));
            }
        }, useAnnotations);
    }

    protected Function<GridBuilder, Void> preRunHook;

    public GlobalTaskRunner runTasksWithAnnotations(Supplier<? extends List<? extends TaskDef>> taskList) {
        return runTasksWithAnnotations(taskList, useAnnotations);
    }

    public GlobalTaskRunner runTasksWithAnnotations(Supplier<? extends List<? extends TaskDef>> taskList, boolean useAnnotations) {
        Configuration projectConf = configureWithAnnotations(useAnnotations);

        GridBuilder grid = newGrid()
            .setShutdownAfterRun(shutdownAfterRun);

        if(input != null){
            grid.setInput(input);
            input = null;
        }

        if (preRunHook != null) {
            preRunHook.apply(grid);
        }

        grid.addAll(taskList.get());

        grid.setAsync(async);

        if (useUI(useAnnotations ? projectConf : null)) {
            return grid.runUi();
        } else {
            return grid.runCli();
        }
    }

    public Configuration configureWithAnnotations(boolean useAnnotations) {
        setProjectVars();

        Configuration projectConf = projectConf();

        if (useAnnotations) {
            configureWithAnnotations(projectConf, null);
        }

        if (!configured) {
            configure();
        }

        return projectConf;
    }


    private Configuration projectConf() {
        return getClass().getAnnotation(Configuration.class);
    }

    private boolean useUI(Configuration annotation) {
        if (global.isSet(bear.useUI)) return global.var(bear.useUI);

        return annotation == null || Annotations.defaultBoolean(annotation, "useUI");
    }

    private Configuration configureWithAnnotations(
        @Nullable Configuration annotation,
        @Nullable Configuration fallbackTo) {

        if (annotation == null && fallbackTo == null) return null;

        String properties = (String) chooseValue("properties", annotation, fallbackTo);

        if (properties != null) {
            File file = new File(properties);

            if (!file.exists() && !file.getName().endsWith(".properties")) {
                file = new File(properties + ".properties");
            }

            Preconditions.checkArgument(file.exists(), "properties file does not exist: %s", file.getAbsolutePath());

            set(main().propertiesFile, file);
        }

        setAnnotation(bear.repositoryURI, chooseValue("vcs", annotation, fallbackTo));
        setAnnotation(bear.vcsBranchName, chooseValue("branch", annotation, fallbackTo));
        setAnnotation(bear.vcsTag, chooseValue("tag", annotation, fallbackTo));
        setAnnotation(bear.stage, chooseValue("stage", annotation, fallbackTo));
        setAnnotation(bear.sshUsername, chooseValue("user", annotation, fallbackTo));
        setAnnotation(bear.sshPassword, chooseValue("password", annotation, fallbackTo));

        Variable[] vars = (Variable[]) chooseValue("variables", annotation, fallbackTo);

        if (vars != null) {
            for (Variable variable : vars) {
                String name = variable.name();
                Preconditions.checkArgument(global.variableRegistry.contains(name), "variable %s was not found in the registry", name);
                global.putConst(name, variable.value());
            }
        }

        return annotation;
    }

    private Object chooseValue(String property, Configuration annotation, Configuration fallbackTo) {
        Object value = null;

        if(annotation != null) {
            Object defaultValue = Annotations.defaultValue(annotation, property);
            value = OpenBean.invoke(annotation, property);
            if(defaultValue.equals(value)) value = null;
        }

        if(value == null && fallbackTo != null) {
            Object defaultValue = Annotations.defaultValue(fallbackTo, property);
            value = OpenBean.invoke(fallbackTo, property);
            if(defaultValue.equals(value)) value = null;
        }

        return value;
    }

    private void setAnnotation(DynamicVariable<String> var, Object value) {
        if (value != null) {
            set(var, (String) value);
        }
    }

    protected TaskDef<Object, TaskResult> rollbackToTask(final String labelOrPath) {
        Preconditions.checkArgument(Strings.isNotEmpty(labelOrPath), "release reference string is empty");

        return defaultDeployment.build()
            .getRollback()
            .addBeforeTask(releases.findReleaseToRollbackTo(labelOrPath));
    }

    protected SELF checkDeployment() {
        Preconditions.checkNotNull(defaultDeployment, "deployment is not present, you need to set BearProject.defaultDeployment field");
        return self();
    }

    private void setProjectVars() {
        Project projectAnn = getClass().getAnnotation(Project.class);

        setAnnotation(bear.fullName, projectAnn.name());
        setAnnotation(bear.name, projectAnn.shortName());
    }

    public SELF setShutdownAfterRun(boolean p) {
        this.shutdownAfterRun = p;
        return self();
    }

    public DeploymentPlugin.Builder getDefaultDeployment() {
        return defaultDeployment;
    }

    public List<Plugin<TaskDef>> getAllOrderedPlugins() {
        try {
            Set<Plugin<TaskDef>> plugins = new HashSet<Plugin<TaskDef>>();

            for (Field field : OpenBean.fieldsOfType(this, Plugin.class)) {
                Plugin plugin = (Plugin) field.get(this);
                Set<Plugin<TaskDef>> set = plugin.getAllPluginDependencies();

                plugins.add(plugin);
                plugins.addAll(set);
            }

            return global.plugins.orderPlugins(plugins);
        } catch (IllegalAccessException e) {
            throw Exceptions.runtime(e);
        }
    }

    public Optional<? extends Plugin<TaskDef>> findShell(String shell){
        Plugin<TaskDef> shellPlugin = null;

        for (Plugin<TaskDef> plugin : getAllShellPlugins(global, this.getClass())) {
            if(shell.equals(plugin.cmdAnnotation())){
                shellPlugin = plugin;
                break;
            }
        }

        return fromNullable(shellPlugin);
    }

    public List<Plugin<TaskDef>> getAllShellPlugins(GlobalContext global, Class<? extends BearProject> aClass) {
        Set<Plugin<TaskDef>> plugins = new LinkedHashSet<Plugin<TaskDef>>();

        plugins.add((Plugin) global.plugin(GenericUnixRemoteEnvironmentPlugin.class));

        for (Field field : OpenBean.fieldsOfType(aClass, Plugin.class)) {
            Plugin plugin = global.getPlugin((Class<? extends Plugin>)field.getType()).get();

            addIfHasShell(plugins, plugin);

            for (Plugin<TaskDef> pl : (Set<Plugin<TaskDef>>) plugin.getAllPluginDependencies()) {
                addIfHasShell(plugins, pl);
            }
        }

        return global.plugins.orderPlugins(plugins);
    }

    private static void addIfHasShell(Set<Plugin<TaskDef>> plugins, Plugin plugin) {
        PluginShellMode shell = plugin.getShell();

        if(shell != null){
            plugins.add(plugin);
        }
    }

    public SELF setInteractiveMode(){
        async = true;
        shutdownAfterRun = false;
        return self();
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public boolean isAsync() {
        return async;
    }

    public static class PulseResult extends TaskResult {
        public PulseResult(Result result) {
            super(result);
        }

        public PulseResult(Throwable e) {
            super(e);
        }
    }

    public DependencyResult pulse() {
        DependencyResult result = new DependencyResult(Result.OK);

        for (Field field : OpenBean.fieldsOfType(this, ServerToolPlugin.class)) {
            try {
                ServerToolPlugin plugin = (ServerToolPlugin) field.get(this);

                result.join(pulse(plugin, Predicates.<String>alwaysTrue()));
            } catch (IllegalAccessException e) {
                throw Exceptions.runtime(e);
            }
        }

        return result;
    }

    protected DependencyResult pulse(ServerToolPlugin serverTool, Predicate<String> bodyPredicate) {
        DependencyResult result = new DependencyResult("pulse from " + serverTool.getClass().getSimpleName());

        HttpClient httpClient = new DefaultHttpClient();

        for (Address address : global.var(global.bear.getStage).addresses) {
            List<String> ports = global.var(serverTool.portsSplit);

            for (String port : ports) {
                URI uri = null;

                try {


                    uri = new URIBuilder()
                        .setScheme("http")
                        .setHost(address.getAddress())
                        .setPort(Integer.parseInt(port))
                        .build();

                    logger.info("sending pulse to {}", uri);

                    HttpGet httpget = new HttpGet(uri);
                    HttpResponse response = httpClient.execute(httpget);

                    int code = response.getStatusLine().getStatusCode();

                    if (code != 200) {
                        result.add("code " + code + " for " + uri);
                        continue;
                    }

                    String body = IOUtils.toString(response.getEntity().getContent());

                    if (!bodyPredicate.apply(body)) {
                        result.add("predicate doesn't match for " + uri);
                        //                        throw new RuntimeException("predicate doesn't match for " + uri);
                    }
                } catch (Exception e) {
                    result.add(String.valueOf(uri) + ": " + e.toString());
                }
            }
        }

        return result;
    }

    public SELF setInput(Object input) {
        this.input = input;
        return self();
    }
}

