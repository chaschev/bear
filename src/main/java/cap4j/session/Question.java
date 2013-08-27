package cap4j.session;

import cap4j.core.CapConstants;
import com.chaschev.chutils.util.Exceptions;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
* User: achaschev
* Date: 8/12/13
* Time: 1:39 AM
*/
public class Question {
    public String question;
    public List<String> options;
    public DynamicVariable<String> var;

    public boolean freeInput;
    public boolean freeInputOption;

    public Question(String question, List<String> options, DynamicVariable<String> var) {
        this.question = question;
        this.options = options;
        this.var = var;
    }

    public void ask(){
        try {

            final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            final String defaultValue = var.getDefaultValue();
            final String defValInput = defaultValue == null ? "" : " [ENTER=" + defaultValue + "]";

            final String newValue;

            if(freeInput){
                System.out.println(System.out.printf("%s", question));
                newValue = br.readLine();
            }else {

                if (options.size() == 1) {
                    if(!freeInputOption){
                        System.out.printf("%s%n", question);

                        newValue = options.get(0);
                        System.out.printf("there is only one option, setting to '%s'%n", newValue);
                    }else{
                        System.out.printf("%s [ENTER=%s]%n", question, options.get(0));

                        String temp = br.readLine().trim();

                        newValue = temp.isEmpty() ? options.get(0) : temp;
                    }
                } else if (!options.isEmpty()) {
                    for (int i = 0; i < options.size(); i++) {
                        String s = options.get(i);
                        System.out.printf("%d) %s%n", i + 1, s);
                    }

                    if(freeInputOption){
                        System.out.printf("%d) %s%n", options.size() + 1, "Other (enter manually)");
                    }

                    System.out.printf("Your choice%s: ", defValInput);

                    final String line = br.readLine();

                    if (line.isEmpty()) {
                        newValue = defaultValue;
                    } else {
                        final int index = Integer.parseInt(line) - 1;
                        if(index == options.size()){
                            System.out.printf("Enter your value: ");

                            newValue = br.readLine();
                        }else{
                            newValue = options.get(index);
                        }
                    }
                } else {
                    System.out.printf("Enter value%s: ", defValInput);

                    newValue = br.readLine();
                }
            }

            var.defaultTo(newValue, true);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public Question setFreeInput(boolean freeInput) {
        this.freeInput = freeInput;
        return this;
    }

    public static String freeQuestion(String question) {
        return freeQuestion(question, CapConstants.strVar());
    }

    public static String freeQuestion(String question, DynamicVariable<String> strVar){
        new Question(question, null, strVar).ask();

        return strVar.defaultValue;

    }

    public static String freeQuestionWithOption(String question, String _default) {
        return freeQuestionWithOption(question, _default, CapConstants.strVar());
    }

    public static String freeQuestionWithOption(String question, String _default, DynamicVariable<String> strVar) {
        new Question(question, Lists.newArrayList(_default), strVar).ask();

        return strVar.defaultValue;
    }
}
