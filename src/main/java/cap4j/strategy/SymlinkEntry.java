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

package cap4j.strategy;

import cap4j.session.DynamicVariable;

import javax.annotation.Nullable;

/**
* User: chaschev
* Date: 7/31/13
*/
public class SymlinkEntry {
    //path in $current
    String sourcePath;
    //dest path in variable
    DynamicVariable<String> destPath;
    //apply chown if needed
    @Nullable
    String owner;

    public SymlinkEntry(String sourcePath, DynamicVariable<String> destPath) {
        this.sourcePath = sourcePath;
        this.destPath = destPath;
    }

    public SymlinkEntry(String sourcePath, DynamicVariable<String> destPath, String owner) {
        this.sourcePath = sourcePath;
        this.destPath = destPath;
        this.owner = owner;
    }
}
