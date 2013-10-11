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

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Version {
    public static final Version ANY = new Version("*");
    public static final Version LATEST = new Version("LATEST");

    String version;

    Version(String version) {
        this.version = version;
    }

    public static Version newVersion(String version) {
        return new Version(version);
    }

    public boolean matches(Version v) {
        return isAny() || version.equals(v.version);
    }

    public boolean isAny() {
        return this == ANY;
    }

    public boolean isLatest() {
        return this == LATEST;
    }

    @Override
    public String toString() {
        return version;
    }

    public static Version fromString(String var) {
        if (var == null || var.equals("*")) {
            return Version.ANY;
        }

        if ("LATEST".equals(var)) return Version.LATEST;

        return Version.newVersion(var);
    }
}
