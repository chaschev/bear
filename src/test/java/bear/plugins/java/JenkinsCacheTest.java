/*
 * Copyright (C) 2014 Andrey Chaschev.
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

package bear.plugins.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.junit.Test;

import java.io.File;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class JenkinsCacheTest {
    @Test
    public void testRead() throws Exception {
        ObjectReader reader = new ObjectMapper().reader(JenkinsCache.class);

        File file = new File("src/test/java/bear/plugins/java/jdks.json");
        JenkinsCache cache = reader.readValue(file);

        assertThat(cache.findJDK("7u51").get().name).isEqualTo("jdk-7u51-linux-x64.tar.gz");
        assertThat(cache.findJDK("7u51", false, true).get().name).isEqualTo("jdk-7u51-windows-x64.exe");

        assertThat(cache.findJDK("6u45").get().name).isEqualTo("jdk-6u45-linux-x64.bin");
    }

    public static void main(String[] args) {
//        Optional<JavaPlugin.JDKFile> optional = JavaPlugin.JenkinsCache.load(new File("jenkins-temp.json"),
//            "http://ftp-nyc.osuosl.org/pub/jenkins/updates/updates/hudson.tools.JDKInstaller.json",
//            "7u51");
//
//        System.out.println(optional.get());

        File file = JenkinsCache.download("7u51", new File("jenkins-temp.json"), new File(".bear"),
            "http://ftp-nyc.osuosl.org/pub/jenkins/updates/updates/hudson.tools.JDKInstaller.json",
            "chaschev@gmail.com", "**");

        System.out.println(file.length());
    }
}
