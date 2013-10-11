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

import bear.session.DynamicVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Console {
    private static final Logger logger = LoggerFactory.getLogger(Console.class);

    protected List<Map.Entry<Nameable, String>> recordedVars = new ArrayList<Map.Entry<Nameable, String>>();

    protected boolean recordingMode = true;
    private GlobalContext global;

    public Console(GlobalContext global) {
        this.global = global;
    }

    public boolean askIfUnset(DynamicVariable<String> var, boolean _default) {
        return askIfUnset(defaultPrompt(var, _default ? "y" : "n"), var, _default);
    }

    public boolean askIfUnset(String prompt, DynamicVariable<String> var, boolean _default) {
        final String s = askIfUnset(prompt, var, _default ? "y" : "n").toLowerCase();

        if (!"y".equals(s) || !"n".equals(s)) {
            throw new RuntimeException("expecting 'y' or 'n'");
        }

        return "y".equals(s);
    }


    public String askIfUnset(DynamicVariable<String> var, String _default) {
        return askIfUnset(defaultPrompt(var, _default), var, _default);
    }

    private static String defaultPrompt(Nameable variableName, String _default) {
        return "Enter value for :" + variableName +
            ((_default == null) ? " [default is '" + _default + "']" : "") +
            ": ";
    }

    public String askIfUnset(String prompt, DynamicVariable<String> var, String _default) {
        if (global.var(var) == null) {
            ask(prompt, var, _default);
        }

        return global.var(var);
    }

    public void ask(String prompt, DynamicVariable<String> var, String _default) {
        System.out.printf(prompt);

        String text = readText();

        if (text.equals("") && _default != null) {
            text = _default;
        }

        global.gvars().set(var, text);

        if (recordingMode) {
            recordedVars.add(new AbstractMap.SimpleEntry<Nameable, String>(var, text));
        }
    }

    public void stopRecording() {
        logger.info("stopping recording");
        recordingMode = false;
    }

    private static String readText() {
        final Scanner scanner = new Scanner(System.in);

        String line;
        String text = "";

        while ((line = scanner.nextLine()).endsWith("\\")) {
            text += line + "\n";
        }

        text += line;

        return text;
    }
}
