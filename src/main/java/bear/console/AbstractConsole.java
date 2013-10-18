package bear.console;

import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface AbstractConsole {
    public static abstract class Terminal{
        public abstract void print(String s);
        public void println(String s){
            print(s + "\n");
        }
    }

    /**
     * @param userCallback Callback for answering questions like 'Enter password'.
     */
    <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command, ConsoleCallback userCallback);
}
