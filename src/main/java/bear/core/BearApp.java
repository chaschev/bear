package bear.core;

import bear.context.AppGlobalContext;
import bear.context.DependencyInjection;
import bear.context.HavingContext;
import bear.session.DynamicVariable;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.TimeZone;

import static bear.session.Variables.undefined;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearApp<GLOBAL extends AppGlobalContext> extends HavingContext<BearApp, GLOBAL> {
    public static final DateTimeZone GMT = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT"));
    public static final DateTimeFormatter RELEASE_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd.HHmmss").withZone(GMT);
//    public final GLOBAL global;

    public final DynamicVariable<String>
        name = undefined("Your app short name to use on paths, i.e. ss-demo"),
        fullName = undefined("Your full app name, i.e. Secure Social Demo");

    protected GLOBAL global;

    public BearApp() {
        super(null);
    }

    public GLOBAL getGlobal() {
        return global;
    }

    public void setGlobal(GLOBAL global) {
        this.global = global;
        this.set$(global);
        DependencyInjection.nameVars(this, global);
    }
}
