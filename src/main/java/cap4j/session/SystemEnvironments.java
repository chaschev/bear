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

package cap4j.session;

import cap4j.cli.CommandLine;
import cap4j.scm.CommandLineResult;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.Iterables.find;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */


//todo don't extends
//todo change to index
public class SystemEnvironments {
    List<SystemEnvironment> implementations = new ArrayList<SystemEnvironment>();

    SystemEnvironment current;

    public SystemEnvironments(SystemEnvironment current) {
        this.current = current;
    }


    public Result sftp(String dest, String host, String path, String user, String pw) {
        return getCurrent().sftp(dest, host, path, user, pw);
    }


    public Result scpLocal(String dest, File... files) {
        throw new UnsupportedOperationException("todo SystemEnvironments.scpLocal");
    }


    public Result mkdirs(String... dirs) {
        return getCurrent().mkdirs(dirs);
    }


    public Result rm(String... paths) {
        return getCurrent().mkdirs(paths);
    }


    public Result copyOperation(String src, String dest, SystemEnvironment.CopyCommandType type, boolean folder) {
        return getCurrent().copyOperation(src, dest, type, folder, null);
    }


    public Result chown(String dest, String octal, String user, boolean recursive) {
        return getCurrent().chown(user, recursive);
    }


    public Result chmod(String perms, boolean recursive, String... files) {
        return getCurrent().chmod(perms, recursive, files);
    }


    public Result writeString(String dest, String s) {
        return getCurrent().writeString(dest, s);
    }


    public String readString(String dest, String _default) {
        return getCurrent().readString(dest, _default);
    }


    public boolean exists(String path) {
        return getCurrent().exists(path);
    }


    public String getName() {
        return getCurrent().getName();
    }


    public String readLink(String path) {
        return getCurrent().readLink(path);
    }


    public Result copy(String src, String dest) {
        return getCurrent().copy(src, dest);
    }


    public Result move(String src, String dest) {
        return getCurrent().move(src, dest);
    }

    /**
     * Should remove an existing link.
     */

    public Result link(String src, String dest) {
        return getCurrent().link(src, dest, null);
    }

    public static interface EnvRunnable {
        Result run(SystemEnvironment system);
    }

    public Result runForEnvironment(final String name, EnvRunnable runnable) {
        return runnable.run(find(implementations, new Predicate<SystemEnvironment>() {

            public boolean apply(SystemEnvironment input) {
                return input.getName().equals(name);
            }
        }));
    }

    private SystemEnvironment getCurrent() {
        return this.current;
    }


    public String joinPath(Iterable<String> strings) {
        return getCurrent().joinPath(strings);
    }


    public List<String> ls(String path) {
        throw new UnsupportedOperationException("todo SystemEnvironments.ls");
    }


    public void zip(String dest, Iterable<String> paths) {
        throw new UnsupportedOperationException("todo SystemEnvironments.zip");
    }


    public void unzip(String file, @Nullable String destDir) {
        throw new UnsupportedOperationException("todo SystemEnvironments.unzip");
    }


    public String newTempDir() {
        throw new UnsupportedOperationException("todo SystemEnvironments.newTempDir");
    }


    public boolean isUnix() {
        throw new UnsupportedOperationException("todo SystemEnvironments.isUnix");
    }


    public <T extends CommandLineResult> T run(CommandLine commandLine) {
        throw new UnsupportedOperationException("todo SystemEnvironments.run");
    }


    public <T extends CommandLineResult> T runVCS(CommandLine<T> stringResultCommandLine) {
        throw new UnsupportedOperationException("todo SystemEnvironments.runVCS");
    }


    public DynamicVariable joinPath(DynamicVariable... vars) {
        return getCurrent().joinPath(vars);
    }

    public boolean add(SystemEnvironment systemEnvironment) {
        return implementations.add(systemEnvironment);
    }

    public List<SystemEnvironment> getImplementations() {
        return implementations;
    }

    public int size() {
        return implementations.size();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("");
        sb.append("").append(implementations);
        return sb.toString();
    }
}
