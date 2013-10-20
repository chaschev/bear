package bear.main;

import chaschev.json.JacksonMapper;
import chaschev.json.Mapper;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class RMIEventToUI extends EventToUI {
    @Nullable
    public String bean;
    public String method;
    public String jsonArrayOfParams;

    final Mapper mapper = new JacksonMapper();

    public RMIEventToUI(String bean, String method, Object... params) {
        super("rmi", "rootCtrl");
        this.bean = bean;
        this.method = method;

        jsonArrayOfParams = mapper.toJSON(Lists.newArrayList(params));
    }
}
