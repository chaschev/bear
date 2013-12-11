package bear.context;

import bear.core.BearApp;
import bear.core.BearMain;
import bear.session.DynamicVariable;
import chaschev.util.JOptOptions;
import com.google.common.base.Preconditions;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;
import joptsimple.util.KeyValuePair;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static bear.context.AppCli.Options.HELP;
import static bear.context.AppCli.Options.VARIABLES;
import static bear.session.Variables.*;
import static com.google.common.collect.Lists.newArrayList;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class AppCli<GLOBAL extends AppGlobalContext<GLOBAL, BEAR_APP>, BEAR_APP extends BearApp<GLOBAL>> extends HavingContext<BearMain, GLOBAL> {
    protected final BEAR_APP bear;

    public final DynamicVariable<File>
        appConfigDir,
        scriptsDir,
        propertiesFile,
        buildDir;

    public final DynamicVariable<Boolean>
        bearify = newVar(false);

    protected final GLOBAL global;
    protected String[] args;
    private boolean shouldExit;

    public AppCli(GLOBAL $, String... args) {
        super($);

        this.bear = $.bear;
        this.args = args;
        this.global = $;

        appConfigDir = convert(newVar(".bear").temp(), TO_FILE);
        scriptsDir = equalTo(appConfigDir);
        propertiesFile = convert(concat(appConfigDir, "/settings.properties"), TO_FILE);
        buildDir = convert(concat(scriptsDir, "/classes"), TO_FILE);
    }

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

        Options options = new Options(args);

        if (options.has(HELP)) {
            System.out.println(options.printHelpOn());
            shouldExit = true;
            return this;
        }

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

            copyResource("CreateNewScript.java", dir);
            copyResource("BearSettings.java", dir);
            copyResource("settings.properties.rename", "settings.properties", dir);

            shouldExit = true;

            return this;
        }

        fileRequired($(scriptsDir));

        return this;
    }

    @SuppressWarnings("unchecked")
    static class Options extends JOptOptions {
        public final static OptionSpec<KeyValuePair> VARIABLES =
            parser.acceptsAll(Arrays.asList("V", "vars"), "set global vars").withRequiredArg()
                .withValuesSeparatedBy(",")
                .withValuesConvertedBy(new KeyValueConverter())
                .ofType(KeyValuePair.class).describedAs("var list");

        public final static OptionSpec<Void> HELP = parser.acceptsAll(newArrayList("h", "help"), "show help");

        public Options(String[] args) {
            super(args);
        }
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
