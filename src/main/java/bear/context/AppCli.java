package bear.context;

import bear.core.BearApp;
import bear.core.BearMain;
import bear.maven.LoggingBooter;
import bear.session.DynamicVariable;
import chaschev.util.RevisionInfo;
import com.google.common.base.Preconditions;
import joptsimple.ValueConverter;
import joptsimple.util.KeyValuePair;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import static bear.context.AppOptions.*;
import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class AppCli<
    GLOBAL extends AppGlobalContext<GLOBAL, BEAR_APP>,
    BEAR_APP extends BearApp<GLOBAL>,
    OPTIONS extends AppOptions>
    extends HavingContext<BearMain, GLOBAL> {
    protected final BEAR_APP bear;


    public final DynamicVariable<String>
        appConfigDirName = newVar(".bear");

    public final DynamicVariable<File>
        appConfigDir = convert(appConfigDirName, TO_FILE),
        scriptsDir = equalTo(appConfigDir),
        propertiesFile = convert(concat(appConfigDir, "/settings.properties"), TO_FILE),
        buildDir = convert(concat(scriptsDir, "/classes"), TO_FILE);

    public final DynamicVariable<Boolean>
        bearify = newVar(false);

    protected final GLOBAL global;
    protected String[] args;
    private boolean shouldExit;
    protected OPTIONS options;

    public AppCli(GLOBAL $, String... args) {
        super($);

        this.bear = $.bear;
        this.args = args;
        this.global = $;
        options = createOptions(args);
    }

    protected abstract OPTIONS createOptions(String... args);

    private static void copyResource(String resource, File bearDir) throws IOException {
        copyResource(resource, resource, bearDir);
    }

    private static void copyResource(String resource, String destName, File bearDir) throws IOException {
        final File file = new File(bearDir, destName);
        System.out.printf("creating %s%n", file.getAbsolutePath());

        IOUtils.copy(BearMain.class.getResourceAsStream("/" + resource), new FileOutputStream(file));
    }

    private static File fileRequired(File settingsFile) {
        Preconditions.checkArgument(settingsFile.exists(), settingsFile.getAbsolutePath() + " does not exist. Use --bearify to create it.");
        return settingsFile;
    }

    boolean shouldExit() {
        return shouldExit;
    }

    public GLOBAL getGlobal() {
        return $;
    }

    //todo move to vars framework
    public AppCli configure() throws IOException {
Map<String, String> env = System.getenv();

        for (Map.Entry<String, String> entry : env.entrySet()) {
            $.convertAndPutConst(entry.getKey(), entry.getValue(),
                $.variableRegistry.getType(entry.getKey()));
        }

        for (KeyValuePair pair : options.getList(VARIABLES)) {
            //todo move registry into global AbstractContext
            $.convertAndPutConst(pair.key, pair.value, $.variableRegistry.getType(pair.key));
        }

        $.loadProperties($(propertiesFile));

        if ($(bearify)) {
            final File dir = $(appConfigDir);

//            System.out.printf("saving to dir %s%n", bearDir.getAbsolutePath());;

            if (!dir.exists()) {
                dir.mkdirs();
            }

            copyResource("settings.properties.rename", "settings.properties", dir);

            shouldExit = true;

            return this;
        }

        fileRequired($(scriptsDir));

        return this;
    }

    protected boolean checkHelpAndVersion() {
        if(options.has(LOG_LEVEL)){
            System.out.println("changing root logger level to " + LOG_LEVEL);
            LoggingBooter.changeLogLevel("root", Level.toLevel(options.get(LOG_LEVEL)));
        }

        if (options.has(HELP)) {
            System.out.println(RevisionInfo.get(getClass()).toString());
            System.out.println();
            System.out.println(options.printHelpOn());
            shouldExit = true;
        }

        if(options.has(VERSION)){
            System.out.println(RevisionInfo.get(AppCli.class).toString());
            shouldExit = true;
        }

        return shouldExit;
    }

    public static class KeyValueConverter implements ValueConverter<KeyValuePair> {
        @Override
        public KeyValuePair convert(String value) {
            return KeyValuePair.valueOf(value);
        }

        @Override
        public Class<KeyValuePair> valueType() {
            return KeyValuePair.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }
}
