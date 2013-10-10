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

import chaschev.util.Exceptions;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CompiledEntry {
    public final Class<?> aClass;
    public final File file;
    public final String type;

    public CompiledEntry(Class<?> aClass, File file, String type) {
        this.aClass = aClass;
        this.file = file;
        this.type = type;
    }

    public String getName() {
        return aClass.getSimpleName();
    }

    public String getText() {
        try {
            return FileUtils.readFileToString(file);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public void saveText(String text) {
        try {
            FileUtils.writeStringToFile(file, text);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }
}
