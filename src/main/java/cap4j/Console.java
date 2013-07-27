package cap4j;

import java.util.*;

/**
 * User: ACHASCHEV
 * Date: 7/27/13
 */
public class Console {
    protected List<Map.Entry<Nameable, String>> recordedVars = new ArrayList<Map.Entry<Nameable, String>>();

    protected boolean recordingMode = true;

    public boolean askIfUnset(Nameable name, boolean _default) {
        return askIfUnset(defaultPrompt(name, _default ? "y" : "n"), name, _default);
    }

    public boolean askIfUnset(String prompt, Nameable name, boolean _default){
        final String s = askIfUnset(prompt, name, _default ? "y" : "n").toLowerCase();

        if(!"y".equals(s) || !"n".equals(s)){
            throw new RuntimeException("expecting 'y' or 'n'");
        }

        return "y".equals(s);
    }

    public String askIfUnset(Nameable variableName, String _default) {
        return askIfUnset(defaultPrompt(variableName, _default), variableName, _default);
    }

    private static String defaultPrompt(Nameable variableName, String _default) {
        return "Enter value for :" + variableName +
            ((_default == null) ? " [default is '" + _default + "']" : "") +
            ": ";
    }

    public String askIfUnset(String prompt, Nameable variableName, String _default){
        Object o = GlobalContext.var(variableName, null);

        if(o == null){

            System.out.printf(prompt);

            String text = readText();

            if(text.equals("") && _default != null) {
                text = _default;
            }

            GlobalContext.gvars().set(variableName, text);

            if(recordingMode){
                recordedVars.add(new AbstractMap.SimpleEntry<Nameable, String>(variableName, text));
            }

            o = text;
        }

        return o.toString();
    }

    public void stopRecording(){
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
