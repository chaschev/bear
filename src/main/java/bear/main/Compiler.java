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

package bear.main;

import bear.context.CompilationResult;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class Compiler {
    protected final String[] extensions = extensions();

    protected List<File> sourceDirs;
    protected File buildDir;

    public abstract String[] extensions();

    Compiler(List<File> sourceDirs, File buildDir) {
        this.sourceDirs = sourceDirs;
        this.buildDir = buildDir;
    }

    public boolean accepts(String extension) {
        return ArrayUtils.contains(extensions, extension);
    }

    public abstract CompilationResult compile(ClassLoader parentCL);

}
