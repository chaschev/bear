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

import chaschev.util.Exceptions;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Versions {
    public static final VersionConstraint ANY = newConstConstraint("ANY", true);
    public static final Version ANY_VERSION = newAnyVersion("ANY", 0);

    private static Version newAnyVersion(final String s, final int compareTo) {
        return new Version() {
            @Override
            public int compareTo(Version o) {
                return compareTo;
            }

            @Override
            public String toString() {
                return s;
            }
        };
    }

    public static final VersionConstraint LATEST = newConstConstraint("LATEST", true);
    public static final Version NOT_INSTALLED = newAnyVersion("NOT_INSTALLED", -1);

    public static final GenericVersionScheme VERSION_SCHEME = new GenericVersionScheme();

    private Versions() {
    }

    public static Version newVersion(String version) {
        try {
            return VERSION_SCHEME.parseVersion(version);
        } catch (InvalidVersionSpecificationException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static VersionConstraint newVersionConstraint(String versionConstrain) {
        try {
            if (versionConstrain == null || versionConstrain.equals("*")) {
                return ANY;
            }

            if ("LATEST".equals(versionConstrain)) return LATEST;


            return VERSION_SCHEME.parseVersionConstraint(versionConstrain);
        } catch (InvalidVersionSpecificationException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static String toString(Version v) {
        return ((v == null || v == ANY) ? "" : v.toString());
    }



    public boolean isAny() {
        return this == ANY_VERSION;
    }

    public boolean isLatest() {
        return this == LATEST;
    }

    public static VersionConstraint fromString(String s) {
        return newVersionConstraint(s);
    }

    private static VersionConstraint newConstConstraint(final String name, final boolean value) {
        return new VersionConstraint() {
            @Override
            public VersionRange getRange() {
                throw new UnsupportedOperationException("todo .getRange");
            }

            @Override
            public Version getVersion() {
                throw new UnsupportedOperationException("todo .getVersion");
            }

            @Override
            public boolean containsVersion(Version version) {
                return value;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    public static String getHighest(VersionConstraint versionConstraint){
        if(versionConstraint == LATEST || versionConstraint == ANY){
            return "";
        }

        if (versionConstraint.getRange() == null) {
            return versionConstraint.getVersion().toString();
        } else {
            VersionRange.Bound upperBound = versionConstraint.getRange().getUpperBound();

            if(upperBound == null){
                return "";
            }

            return upperBound.getVersion().toString();
        }
    }
}
