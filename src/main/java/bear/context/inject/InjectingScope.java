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

package bear.context.inject;

import java.lang.annotation.ElementType;

/**
 * I.e. HashMap, HashSet, false means anything extending these classes.
 */
public class InjectingScope {
    public final Class[] classes;
    public final boolean strict;
    public final boolean isSuper;
    public final ElementType elementType;

    public InjectingScope(boolean strict, Class... classes) {
        this.strict = strict;
        this.classes = classes;
        this.isSuper = false;
        elementType = null;
    }

    public InjectingScope(boolean strict, ElementType elementType, boolean aSuper, Class... classes) {
        this.elementType = elementType;
        this.classes = classes;
        this.strict = strict;
        isSuper = aSuper;
    }
}
