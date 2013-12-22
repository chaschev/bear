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
import bear.plugins.misc.ReleasesPlugin;
import bear.session.DynamicVariable;
import bear.task.*;
import chaschev.lang.OpenBean;
import chaschev.lang.reflect.Annotations;
import chaschev.lang.reflect.MethodDesc;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.core.helpers.Strings;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

import static com.google.common.base.Objects.firstNonNull;
import static java.util.Collections.singletonList;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class BearProject<SELF extends BearProject> {
    protected GlobalContextFactory factory;
    protected GlobalContext global;
    private boolean configured;
    private Map<Object, Object> variables;
    private BearMain bearMain;
    protected Bear bear;

    protected DeploymentPlugin.Builder defaultDeployment;

    protected ReleasesPlugin releases;

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

    public BearProject withMap(Map<Object, Object> variables){
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
            Class<? extends Plugin<Task, TaskDef<?>>> aClass = (Class<? extends Plugin<Task, TaskDef<?>>>) field.getType();

            factory.requirePlugins(aClass);
        }

        factory.initPluginsAndWire(this);

        Iterable<Field> fields = OpenBean.fieldsOfType(this, GridBuilder.class);

        for (Field field : fields) {
            ((GridBuilder)field.get(this)).init(this);
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

    public void run(){
        BearMain.run(this, variables, true);
    }

    public <T> SELF set(DynamicVariable<T> var, T value) {
        global.putConst(var, value);
        return self();
    }

    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF) this;
    }

    SELF injectMain(BearMain bearMain){
        this.bearMain = bearMain;
        return self();
    }

    public synchronized BearMain main() {
        if(bearMain == null){
            bearMain = new BearMain(global, BearMain.createCompilerManager());
        }

        return bearMain;
    }

    public GridBuilder newGrid() {
        GridBuilder gb = new GridBuilder();

        gb.project = this;
        gb.bearMain = main();

        return gb;
    }

    public TaskDef<Task> newDeployTask(){
        checkDeployment();
        return defaultDeployment.build();
    }

    protected List<TaskDef<Task>> startServiceTaskDefs(){
        checkDeployment();

        return defaultDeployment.getStartService().createTasksToList(new ArrayList<TaskDef<Task>>());
    }

    protected List<TaskDef<Task>> stopServiceTaskDefs(){
        checkDeployment();

        return defaultDeployment.getStopService().createTasksToList(new ArrayList<TaskDef<Task>>());
    }

    public void start(){
        runTasksWithAnnotations(new Supplier<List<TaskDef<Task>>>() {
            @Override
            public List<TaskDef<Task>> get() {
                return startServiceTaskDefs();
            }
        });
    }

    public void stop(){
        runTasksWithAnnotations(new Supplier<List<TaskDef<Task>>>() {
            @Override
            public List<TaskDef<Task>> get() {
                return stopServiceTaskDefs();
            }
        });
    }

    public void deploy(){
        runTasksWithAnnotations(new Supplier<List<TaskDef<Task>>>() {
            @Override
            public List<TaskDef<Task>> get() {
                return singletonList(defaultDeployment.build());
            }
        });
    }

    public void setup(){
        setup(true);
    }

    public void setup(boolean autoInstall){
        if(autoInstall){
            set(bear.verifyPlugins, true);
            set(bear.autoInstallPlugins, true);
            set(bear.checkDependencies, true);
        }

        runTasksWithAnnotations(new Supplier<List<InstallationTaskDef<InstallationTask>>>() {
            @Override
            public List<InstallationTaskDef<InstallationTask>> get() {
                return singletonList(global.tasks.setup);
            }
        });
    }

    public void rollbackTo(final String ref){
        runTasksWithAnnotations(new Supplier<List<TaskDef<Task>>>() {
            @Override
            public List<TaskDef<Task>> get() {
                return singletonList(rollbackToTask(ref));
            }
        });
    }

    public void invoke(){
        String method = getClass().getAnnotation(Project.class).method();

        Preconditions.checkArgument(!method.isEmpty(), "method() must not be empty for @Project when not providing a invoking a project");

        invoke(method);
    }

    public void invoke(String method, Object... params){
        setProjectVars();

        Configuration projectConfiguration = load(projectConf());

        MethodDesc methodDesc = OpenBean.getClassDesc(this.getClass()).getMethodDesc(method, false, params);

        Configuration methodConfiguration = load(methodDesc.getMethod().getAnnotation(Configuration.class));

        global.put(bear.useUI, useUI(firstNonNull(methodConfiguration, projectConfiguration)));

        Object result = methodDesc.invoke(this, params);

        System.out.println("returned result: " + result);
    }

    public SELF run(final List<TaskCallable<TaskDef>> callables) {
        runTasksWithAnnotations(new Supplier<List<? extends TaskDef>>() {
            @Override
            public List<? extends TaskDef> get() {
                return Lists.newArrayList(Lists.transform(callables, new Function<TaskCallable<TaskDef>, TaskDef>() {
                    @Override
                    public TaskDef apply( TaskCallable<TaskDef> input) {
                        return new TaskDef(input);
                    }
                }));
            }
        });
        return self();
    }

    public void runTasksWithAnnotations(Supplier<? extends List<? extends TaskDef>> taskList) {
        setProjectVars();

        Configuration projectConf = load(projectConf());

        GridBuilder gridBuilder = configure()
            .newGrid()
            .addAll(taskList.get());

        if(useUI(projectConf)){
            gridBuilder.run();
        }else{
            gridBuilder.runCli();
        }
    }

    private Configuration projectConf() {
        return getClass().getAnnotation(Configuration.class);
    }

    private boolean useUI(Configuration annotation) {
        if(global.isSet(bear.useUI)) return global.var(bear.useUI);

        return annotation == null || Annotations.defaultBoolean(annotation, "useUI");
    }

    private Configuration load(Configuration annotation) {
        if(annotation == null) return null;
        if(!"".equals(annotation.properties())) {
            File file = new File(annotation.properties());

            if(!file.exists() && !file.getName().endsWith(".properties")){
                 file = new File(annotation.properties() + ".properties");
            }

            Preconditions.checkArgument(file.exists(), "properties file does not exist: %s", file.getAbsolutePath());

            set(main().propertiesFile, file);
        }

        setAnnotation(bear.repositoryURI, annotation.vcs());
        setAnnotation(bear.vcsBranchName, annotation.branch());
        setAnnotation(bear.vcsTag, annotation.tag());
        setAnnotation(bear.stage, annotation.stage());

        if(annotation.variables() != null){
            for (Variable variable : annotation.variables()) {
                String name = variable.name();
                Preconditions.checkArgument(global.variableRegistry.contains(name), "variable %s was not found in the registry", name);
                global.putConst(name, variable.value());
            }
        }

        return annotation;
    }

    private void setAnnotation(DynamicVariable<String> var, String value) {
        if(!"".equals(value)) {
            set(var, value);
        }
    }


    protected TaskDef<Task> rollbackToTask(final String labelOrPath){
        Preconditions.checkArgument(Strings.isNotEmpty(labelOrPath), "release reference string is empty");

        return  defaultDeployment.build()
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

    public static class Test<T>{
        public static void main(String[] args) {
            System.out.println("12242735735435435373".matches("^[0-9]+$"));
        }    }
}

