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

import bear.core.BearProject;
import bear.core.GlobalContextFactory;
import chaschev.lang.OpenBean;
import chaschev.lang.reflect.ConstructorDesc;
import chaschev.util.Exceptions;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CompiledEntry<T> {
    public final Class<? extends T> aClass;
    public final File file;
    public final String type;

    public CompiledEntry(Class<? extends T> aClass, File file, String type) {
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

    public Object newInstance(Object... params){
        ConstructorDesc<?> desc = OpenBean.getConstructorDesc(aClass, params);

        if(desc == null && BearProject.class.isAssignableFrom(aClass)){
            try {
                Object obj = aClass.newInstance();

                OpenBean.setField(obj, "factory", GlobalContextFactory.INSTANCE);
                OpenBean.setField(obj, "global", GlobalContextFactory.INSTANCE.getGlobal());

                return obj;
            } catch (Exception e) {
                throw Exceptions.runtime(e);
            }
        }

        return desc.newInstance(params);
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CompiledEntry{");
        sb.append("aClass=").append(aClass);
        sb.append(", file=").append(file);
        sb.append(", type='").append(type).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
