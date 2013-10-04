package bear.console;

import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class AbstractConsole {
    public static abstract class Terminal{
        public abstract void print(String s);
        public void println(String s){
            print(s + "\n");
        }
    }

    public abstract <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command, ConsoleCallback callback);

}
