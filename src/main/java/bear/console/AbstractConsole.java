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

    /**
     * @param userCallback Callback for answering questions like 'Enter password'.
     */
    public <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command, ConsoleCallback userCallback){
//        command.textListener = new AbstractConsoleCommand.TextListener() {
//            @Override
//            public void on(CharSequence newText, StringBuilder wholeText) {
//                throw new UnsupportedOperationException("todo .on");
//            }
//        };

        return null;
    }

}
