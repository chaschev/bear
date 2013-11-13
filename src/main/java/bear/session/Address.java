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

package bear.session;

import bear.context.Var;
import bear.core.Role;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class Address {
    @Var("sessionHostname")
    String name;

    @Nullable
    private Set<Role> roles;

    public Address() {
    }

    public Address(String name) {
        this.name = name;
    }

    public String getName(){
        return name;
    }


    public abstract String getAddress();


    public final Set<Role> getRoles() {
        if(roles == null){
            roles = new HashSet<Role>();
        }

        return roles;
    }
}
