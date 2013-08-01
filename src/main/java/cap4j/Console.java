package cap4j;

import cap4j.session.DynamicVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * User: ACHASCHEV
 * Date: 7/27/13
 */
public class Console {
    private static final Logger logger = LoggerFactory.getLogger(Console.class);

    protected List<Map.Entry<Nameable, String>> recordedVars = new ArrayList<Map.Entry<Nameable, String>>();

    protected boolean recordingMode = true;

    public boolean askIfUnset(DynamicVariable<String> var, boolean _default) {
        return askIfUnset(defaultPrompt(var, _default ? "y" : "n"), var, _default);
    }

    public boolean askIfUnset(String prompt, DynamicVariable<String> var, boolean _default){
        final String s = askIfUnset(prompt, var, _default ? "y" : "n").toLowerCase();

        if(!"y".equals(s) || !"n".equals(s)){
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

    public String askIfUnset(String prompt, DynamicVariable<String> var, String _default){
        Object o = GlobalContext.var(var);

        if(o == null){

            System.out.printf(prompt);

            String text = readText();

            if(text.equals("") && _default != null) {
                text = _default;
            }

            GlobalContext.gvars().set(var, text);

            if(recordingMode){
                recordedVars.add(new AbstractMap.SimpleEntry<Nameable, String>(var, text));
            }

            o = text;
        }

        return o.toString();
    }

    public void stopRecording(){
        logger.info("stopping recording");
        recordingMode = false;
    }

    private static String readText() {
        final Scanner scanner = new Scanner(System.in);

        String line;
        String text = "";

        while((line = scanner.nextLine()).endsWith("\\")){
            text += line + "\n";
        }

        text += line;

        return text;
    }
}
