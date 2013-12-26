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

import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Role {
    public static final Role
        db = role("db"),
        web = role("web"),
        backend = role("backend");

    @Nonnull
    public final String role;

    public Role(String role) {
        Preconditions.checkNotNull(role);
        this.role = role;
    }

    public static Role role(String s) {
        return new Role(s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Role role1 = (Role) o;

        if (!role.equals(role1.role)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return role.hashCode();
    }

    @Override
    public String toString() {
        return role;
    }
}
