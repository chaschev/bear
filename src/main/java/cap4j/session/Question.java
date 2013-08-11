package cap4j.session;

import com.chaschev.chutils.util.Exceptions;

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

    public Question(String question, List<String> options, DynamicVariable<String> var) {
        this.question = question;
        this.options = options;
        this.var = var;
    }

    public void ask(){
        try {
            System.out.printf("%s%n", question);

            final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            final String defaultValue = var.getDefaultValue();
            final String defValInput = defaultValue == null ? "" : " [ENTER=" + defaultValue + "]";

            final String newValue;

            if(options.size() == 1){
                newValue = options.get(0);
            }else
            if(!options.isEmpty()){
                for (int i = 0; i < options.size(); i++) {
                    String s = options.get(i);
                    System.out.printf("%d) %s%n", i+1, s);
                }


                System.out.printf("Your choice%s: ", defValInput);

                final String line = br.readLine();

                if(line.isEmpty()){
                    newValue = defaultValue;
                }else{
                    newValue = options.get(Integer.parseInt(line) - 1);
                }
            }else{
                System.out.printf("Enter value%s: ", defValInput);

                newValue = br.readLine();
            }

            var.defaultTo(newValue);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }
}
