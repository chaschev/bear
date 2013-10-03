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

package cap4j.plugins;

import cap4j.core.Cap;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.session.DynamicVariable;
import cap4j.task.*;
import cap4j.vcs.CommandLineResult;
import com.chaschev.chutils.util.Exceptions;
import com.chaschev.chutils.util.OpenBean2;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.StringUtils.getLevenshteinDistance;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Plugin {
    public String name;
    public final Cap cap;
    protected GlobalContext global;
    protected Dependencies dependencies = new Dependencies();

    public Plugin(GlobalContext global) {
        this.global = global;
        this.cap = global.cap;
        name = getClass().getSimpleName();
    }

    public Task newSession(SessionContext $){
        throw new UnsupportedOperationException("todo");
    }

    public static void nameVars(Object obj) {
        final Class<?> aClass = obj.getClass();
        final String className = aClass.getSimpleName();
        final Field[] fields = OpenBean2.getClassDesc(aClass).fields;

        try {
            for (Field field : fields) {
                if (!DynamicVariable.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                final DynamicVariable var = (DynamicVariable) field.get(obj);
                Preconditions.checkNotNull(var, field.getName() + " is null!");
                var.setName(className + "." + field.getName());
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void initPlugin() {

    }

    public abstract InstallationTaskDef<? extends InstallationTask> getInstall();

    public DependencyResult checkPluginDependencies(){
        return DependencyResult.OK;
    }

    @Override
    public String toString() {
        return name;
    }

    protected DependencyResult require(Class... pluginClasses) {
        final DependencyResult r = new DependencyResult(this.getClass().getSimpleName());

        for (Class pluginClass : pluginClasses) {
            require(r, pluginClass);
        }

        return r.updateResult();
    }

    public static class CompositeConsoleArrival {
        ArrivalEntry[] entries;

        public CompositeConsoleArrival(int size) {
            entries = new ArrivalEntry[size];
        }

        public void addArrival(int i, CommandLineResult text) {
            throw new UnsupportedOperationException("todo CompositeConsoleArrival.addArrival");
        }

        public static class ArrivalEntry {
            CommandLineResult result;

            public ArrivalEntry(CommandLineResult result) {
                this.result = result;
            }
        }

        public static class EqualityGroups{
            List<EqualityGroup> groups = new ArrayList<EqualityGroup>();

            int size;

            public EqualityGroups(List<EqualityGroup> groups) {
                this.groups = groups;

                for (EqualityGroup group : groups) {
                    size += group.size();
                }
            }

            public Optional<EqualityGroup> getMajorityGroup(){
                return Iterables.tryFind(groups, new Predicate<EqualityGroup>() {
                    @Override
                    public boolean apply(EqualityGroup input) {
                        return input.size() > size / 2;
                    }
                });
            }

            public ArrayList<EqualityGroup> getMinorGroups(){
                return newArrayList(filter(groups, not(equalTo(getMajorityGroup().orNull()))));
            }
        }

        public static class EqualityGroup{
            String text;
            int firstEntry;
            List<Integer> entries = new ArrayList<Integer>();

            private EqualityGroup(String text, int firstEntry) {
                this.text = text;
                this.firstEntry = firstEntry;
            }

            public boolean sameGroup(ArrivalEntry entry) {
                String otherText = entry.result.text;

                return getLevenshteinDistance(text, otherText, 5000) * 1.0 /
                    (text.length() + otherText.length()) < 5;
            }

            public void add(ArrivalEntry entry, int i) {
                throw new UnsupportedOperationException("todo EqualityGroup.add");
            }

            public int size() {
                return entries.size();
            }
        }

        protected int thresholdDistance;

        protected List<EqualityGroup> divideIntoGroups(){
            List<EqualityGroup> groups = new ArrayList<EqualityGroup>();

            groups.add(new EqualityGroup(entries[0].result.text, 0));

            for (int i = 1; i < entries.length; i++) {
                ArrivalEntry entry = entries[i];

                for (EqualityGroup group : groups) {
                    if(group.sameGroup(entry)){
                        group.add(entry, i);
                    }
                }
            }

            return groups;
        }
    }

    public static abstract class ConsoleCallback {
        public abstract void progress(String buffer, String wholeText);
        public abstract void whenDone(CommandLineResult result);
    }

    public static abstract class AbstractConsole {
        public abstract CompositeConsoleArrival.EqualityGroups sendCommand(String text, ConsoleCallback callback, long timeoutMs);

        public void sendCommand(String text, ConsoleCallback callback, long timeout, TimeUnit timeUnit){
            sendCommand(text, callback, timeUnit.toMillis(timeout));
        }
    }

    public static class CompositeConsole extends AbstractConsole{
        List<AbstractConsole> consoles = new ArrayList<AbstractConsole>();
        Progress progress;

        public static abstract class Progress{
            public abstract void on(int console, String interval, String wholeText);
        }


        @Override
        public CompositeConsoleArrival.EqualityGroups sendCommand(final String text, ConsoleCallback callback, long timeoutMs) {
            final CompositeConsoleArrival consoleArrival = new CompositeConsoleArrival(consoles.size());

            final CountDownLatch latch = new CountDownLatch(consoles.size());

            for (int i = 0; i < consoles.size(); i++) {
                final AbstractConsole console = consoles.get(i);

                final int finalI = i;
                console.sendCommand(text, new ConsoleCallback() {
                    @Override
                    public void progress(String interval, String wholeText) {
                        progress.on(finalI, interval, wholeText);
                    }

                    @Override
                    public void whenDone(CommandLineResult result) {
                        consoleArrival.addArrival(finalI, result);
                        latch.countDown();
                    }
                }, timeoutMs);
            }

            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw Exceptions.runtime(e);
            }

            return new CompositeConsoleArrival.EqualityGroups(consoleArrival.divideIntoGroups());
        }
    }

    public AbstractConsole getConsole(){
        throw new UnsupportedOperationException("plugin does not support console");
    }

    public boolean isConsoleSupported(){
        try {
            getConsole();
            return true;
        }catch (UnsupportedOperationException e){
            return !e.getMessage().contains("plugin does not support console");
        }
    }

    protected void require(DependencyResult r, Class<? extends Plugin> pluginClass) {
        final Plugin plugin = global.getPlugin(pluginClass);

        if(plugin == null){
            r.add(plugin.getClass().getSimpleName() + " plugin is required");
        }
    }

    public Dependencies getDependencies() {
        return dependencies;
    }

    protected final Dependencies addDependency(Dependency... dependencies) {
        return this.dependencies.addDependencies(dependencies);
    }
}
