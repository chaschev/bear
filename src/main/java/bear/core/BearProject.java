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
import bear.session.DynamicVariable;
import bear.task.Task;
import bear.task.TaskDef;
import chaschev.lang.OpenBean;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class BearProject<SELF extends BearProject> {
    protected GlobalContextFactory factory;
    protected GlobalContext global;
    private boolean configured;
    private Map<Object, Object> variables;
    private BearMain bearMain;

    protected BearProject() {
        this.factory = GlobalContextFactory.INSTANCE;
        global = factory.getGlobal();
    }

    protected BearProject(GlobalContextFactory factory) {
        this.factory = factory;
        global = factory.getGlobal();
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

    public GridBuilder defaultGrid(){
        throw new UnsupportedOperationException("grid not set");
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

    public final SELF configure() throws Exception {
        return configure(factory);
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

//    public GridBuilder grid() {
//        try {
//            return defaultGrid();
//        } catch (UnsupportedOperationException e){
//            throw e;
////            if(e.getMessage().equals("grid not set")){
////                if(bearMain == null){
////                    bearMain =
////                        new BearMain(global)
////                        .configure();
////                }
////
////                bearMain.newProject()
////            }else{
////                throw e;
////            }
//        }
//    }

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

    void injectMain(BearMain bearMain){
        this.bearMain = bearMain;
    }

    public synchronized BearMain main() {
        if(bearMain == null){
            bearMain = new BearMain(global);
        }
        return bearMain;
    }


}

