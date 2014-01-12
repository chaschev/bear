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
import chaschev.util.RevisionInfo;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.commons.lang3.text.WordUtils;

import java.io.IOException;

import static java.lang.Character.toUpperCase;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ProjectGenerator {

    private final String dashedTitle;
    private final String user;
    private final String password;
    private final String host;

    private String projectTitle;

    public ProjectGenerator(String dashedTitle, String user, String password, String host) {
        this.dashedTitle = dashedTitle;
        this.user = user;
        this.password = password;
        this.host = host;
        projectTitle = toCamelHumpCase(dashedTitle) + "Project";

    }

    public String generatePom(String dashedTitle){

        try {
            String s = "/templates/pom.xml";
            return StrSubstitutor.replace(readResource(s), ImmutableMap.<String, String>builder()
                .put("dashedTitle", dashedTitle)
                .put("bearVersion", RevisionInfo.get(getClass()).getVersion())
                .build());
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static String readResource(String path) throws IOException {
        return Resources.toString(
            ProjectGenerator.class.getResource(path),
            Charsets.UTF_8
        );
    }

    public String processTemplate(final String templateName){

        try {

            return StrSubstitutor.replace(readResource("/templates/" + templateName), ImmutableMap.<String, String>builder()
                .put("dashedTitle", this.dashedTitle)
                .put("projectTitle", projectTitle)
                .put("user", this.user)
                .put("password", this.password)
                .put("host", this.host)
                .put("spacedTitle", toSpacedTitle(projectTitle))
                .build());
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static String toCamelHumpCase(String dashedTitle) {
        StringBuilder sb = new StringBuilder(dashedTitle.length());

        sb.append(toUpperCase(dashedTitle.charAt(0)));

        for (int i = 1; i < dashedTitle.length(); i++) {
            char ch = dashedTitle.charAt(i);
            if(ch == '-'){
                sb.append(toUpperCase(dashedTitle.charAt(i + 1)));
                i++;
            }else{
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    public String getShortName(){
        return WordUtils.uncapitalize(toCamelHumpCase(dashedTitle));
    }

    public static String toSpacedTitle(String dashedTitle) {
        StringBuilder sb = new StringBuilder(dashedTitle.length());

        sb.append(toUpperCase(dashedTitle.charAt(0)));

        for (int i = 1; i < dashedTitle.length(); i++) {
            char ch = dashedTitle.charAt(i);
            if(ch == '-'){
                sb.append(' ');
                sb.append(toUpperCase(dashedTitle.charAt(i + 1)));
                i++;
            }else{
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    public String getProjectTitle() {
        return projectTitle;
    }
}
