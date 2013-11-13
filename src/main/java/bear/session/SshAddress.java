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

import bear.core.Bear;
import bear.context.Var;
import bear.context.WireFields;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@WireFields(Bear.class)
public class SshAddress extends Address {
    @Var("sshUsername")
    public String username;

    @Var("sshPassword")
    public String password;

    @Var("sessionAddress")
    public String address;

    public SshAddress() {
    }

    public SshAddress(String name, String username, String password, String address) {
        super(name);
        this.username = username;
        this.password = password;
        this.address = address;
    }

    public SshAddress(String username, String password, String address) {
        super(address);
        this.username = username;
        this.password = password;
        this.address = address;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("username='").append(username).append('\'');
        sb.append(", address='").append(address).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String getName() {
        return address;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SshAddress that = (SshAddress) o;

        if (!address.equals(that.address)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}
