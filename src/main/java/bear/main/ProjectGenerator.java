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

import bear.plugins.Plugin;
import bear.plugins.grails.GrailsPlugin2;
import bear.plugins.java.JavaPlugin;
import bear.plugins.java.PlayPlugin;
import bear.plugins.java.TomcatPlugin;
import bear.plugins.maven.MavenPlugin;
import bear.plugins.mongo.MongoDbPlugin;
import bear.plugins.mysql.MySqlPlugin;
import bear.plugins.nodejs.NodeJsPlugin;
import chaschev.lang.OpenStringBuilder;
import chaschev.util.Exceptions;
import chaschev.util.RevisionInfo;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.commons.lang3.text.WordUtils;

import java.io.IOException;
import java.util.List;

import static java.lang.Character.toUpperCase;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ProjectGenerator {

    private final String dashedTitle;
    private final String user;
    private final String password;
    private final List<String> hosts;
    private final String fields;
    private final String imports;

    private String projectTitle;

    public List<String> template;
    public String oracleUser;
    public String oraclePassword;

    public ProjectGenerator(String dashedTitle, String user, String password, List<String> hosts, List<String> templates) {
        this.dashedTitle = dashedTitle;
        this.user = user;
        this.password = password;
        this.hosts = hosts;

        projectTitle = toCamelHumpCase(dashedTitle) + "Project";

        StringBuilder fieldsSB = new StringBuilder();
        StringBuilder importsSB = new StringBuilder();

        if(templates.contains("java")){
            addPlugin(JavaPlugin.class, fieldsSB, importsSB);
            addPlugin(MavenPlugin.class, fieldsSB, importsSB);
        }

        if(templates.contains("grails")){
            addPlugin(GrailsPlugin2.class, fieldsSB, importsSB);
        }

        if(templates.contains("tomcat")){
            addPlugin(TomcatPlugin.class, fieldsSB, importsSB);
        }

        if(templates.contains("play")){
            addPlugin(PlayPlugin.class, fieldsSB, importsSB);
        }

        if(templates.contains("nodejs")){
            addPlugin(NodeJsPlugin.class, fieldsSB, importsSB);
        }

        if(templates.contains("mysql")){
            addPlugin(MySqlPlugin.class, fieldsSB, importsSB);
        }

        if(templates.contains("mongodb")){
            addPlugin(MongoDbPlugin.class, fieldsSB, importsSB);
        }

        if(fieldsSB.length() != 0){
            fieldsSB.setLength(fieldsSB.length() - 1);
        }

        if(importsSB.length() != 0){
            importsSB.setLength(importsSB.length() - 1);
        }

        fields = fieldsSB.toString();

        imports = importsSB.toString();
    }

    private void addPlugin(Class<? extends Plugin> plugin, StringBuilder fieldsSB, StringBuilder importsSB) {
        String simpleName = plugin.getSimpleName();

        String varName = WordUtils.uncapitalize(StringUtils.substringBeforeLast(simpleName, "Plugin"));

        fieldsSB.append("    ").append(simpleName).append(" ").append(varName).append("\n");

        importsSB.append("import ").append(plugin.getName()).append("\n");
    }

    public String generatePom(String dashedTitle) {

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

    public String processTemplate(final String templateName) {
        try {
            return StrSubstitutor.replace(readResource("/templates/" + templateName), ImmutableMap.<String, String>builder()
                .put("dashedTitle", dashedTitle)
                .put("projectTitle", projectTitle)
                .put("user", user)
                .put("password", Strings.nullToEmpty(password))
                .put("oracleUser", Strings.nullToEmpty(oracleUser))
                .put("oraclePassword", Strings.nullToEmpty(oraclePassword))
                .put("hosts", getHosts())
                .put("spacedTitle", toSpacedTitle(projectTitle))
                .put("fields", fields)
                .put("imports", imports)
                .build());
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    protected String getHosts() {
        OpenStringBuilder sb = new OpenStringBuilder();
        List<String> numbers = Lists.newArrayList("u-1", "u-2", "u-3");

        for (int i = 0; i < hosts.size(); i++) {
            addQuick(sb, numbers.get(i), Joiner.on(", ").join(hosts.subList(0, i + 1)));
        }

        addQuick(sb, "all", Joiner.on(", ").join(hosts));

        sb.setLength(sb.length() - 1);

        return sb.toString();
    }

    protected static void addQuick(OpenStringBuilder sb, String number, String hostsString) {
        sb.append(Strings.repeat("    ", 3)).append(".addQuick(\"").append(number).append("\", \"")
            .append(hostsString).append("\")\n");
    }

    public static String toCamelHumpCase(String dashedTitle) {
        StringBuilder sb = new StringBuilder(dashedTitle.length());

        sb.append(toUpperCase(dashedTitle.charAt(0)));

        for (int i = 1; i < dashedTitle.length(); i++) {
            char ch = dashedTitle.charAt(i);
            if (ch == '-') {
                sb.append(toUpperCase(dashedTitle.charAt(i + 1)));
                i++;
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    public String getShortName() {
        return WordUtils.uncapitalize(toCamelHumpCase(dashedTitle));
    }

    public static String toSpacedTitle(String dashedTitle) {
        StringBuilder sb = new StringBuilder(dashedTitle.length());

        sb.append(toUpperCase(dashedTitle.charAt(0)));

        for (int i = 1; i < dashedTitle.length(); i++) {
            char ch = dashedTitle.charAt(i);
            if (ch == '-') {
                sb.append(' ');
                sb.append(toUpperCase(dashedTitle.charAt(i + 1)));
                i++;
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    public String getProjectTitle() {
        return projectTitle;
    }
}
