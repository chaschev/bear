package bear.console;

import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class AbstractConsoleCommand<T extends CommandLineResult>{
    protected long timeoutMs;
    protected StringBuilder output = new StringBuilder(8192);

    protected TextListener textListener;

    public interface TextListener{
        void on(CharSequence newText, StringBuilder wholeText);
    }

    public abstract String asText();

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public AbstractConsoleCommand<T> append(CharSequence s) {
        output.append(s);

        if(textListener != null){
            textListener.on(s, output);
        }

        return this;
    }

    public StringBuilder getOutput() {
        return output;
    }


}
