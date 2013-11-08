/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.main;

import bear.main.event.EventToUI;
import bear.main.event.EventWithId;
import bear.main.event.NewSessionConsoleEventToUI;
import chaschev.js.Bindings;
import chaschev.js.ExceptionWrapper;
import chaschev.json.JacksonMapper;
import chaschev.json.Mapper;
import chaschev.lang.OpenBean;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static bear.core.SessionContext.randomId;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearFX {
    public final FXConf conf;
    final Properties bearProperties;
    public final Facade facade = new Facade();
    public final BearFXApp bearFXApp;
    public final Bindings.FileManager fileManager = new Bindings.FileManager() {
        @Override
        public String openFileDialog(String dir) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialDirectory(new File(dir));

            File file = fileChooser.showOpenDialog(bearFXApp.stage);

            return file == null ? null : file.getAbsolutePath();
        }
    };

    private static final Logger logger = LoggerFactory.getLogger(BearFX.class);
    private static final Logger jsLogger = LoggerFactory.getLogger("js");

    public class Facade {
        public Object call(String delegate, String method, Object... params) {
            try {
                Object delegateBean = OpenBean.getFieldValue(BearFX.this, delegate);
                return OpenBean.invoke(delegateBean, method, params);
            } catch (Exception e) {
                return new ExceptionWrapper(e);
            }
        }

        public Object getScriptText() throws IOException {
            try {
                return conf.getScriptText();
            } catch (Exception e) {
                return new ExceptionWrapper(e);
            }
        }
    }

    public Object call(String delegate, String method) { return facade.call(delegate, method); }
    public Object call(String delegate, String method, Object p1) { return facade.call(delegate, method, p1); }
    public Object call(String delegate, String method, Object p1, Object p2) { return facade.call(delegate, method, p1, p2); }
    public Object call(String delegate, String method, Object p1, Object p2, Object p3) { return facade.call(delegate, method, p1, p2,p3); }
    public Object call(String delegate, String method, Object p1, Object p2, Object p3, Object p4) { return facade.call(delegate, method, p1, p2,p3,p4); }
    public Object call(String delegate, String method, Object p1, Object p2, Object p3, Object p4, Object p5) { return facade.call(delegate, method, p1, p2,p3,p4,p5); }
    public Object call(String delegate, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) { return facade.call(delegate, method, p1, p2,p3,p4,p5,p6); }
    public Object call(String delegate, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) { return facade.call(delegate, method, p1, p2,p3,p4,p5,p6,p7); }
    public Object call(String delegate, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) { return facade.call(delegate, method, p1, p2,p3,p4,p5,p6,p7,p8); }

    public BearFX(BearFXApp bearFXApp, FXConf conf, Properties bearProperties) {
        this.bearFXApp = bearFXApp;
        this.conf = conf;
        this.bearProperties = bearProperties;
//        conf.bearFX = this;
    }

    public static class BearFXApp extends Application {
        private WebEngine webEngine;
        private BearFX bearFX;

        private final Mapper mapper = new JacksonMapper();

        Bindings bindings = new Bindings();
        Stage stage;

        public void sendMessageToUI(EventToUI eventToUI){
            if (eventToUI instanceof EventWithId) {
                String id = ((EventWithId) eventToUI).getId();
                Preconditions.checkNotNull(id, "id is null for %s", eventToUI);
            }

            final String s = mapper.toJSON(eventToUI);

            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    webEngine.executeScript("Java.receiveEvent(" + s + ")");
                }
            });
        }

        public void runLater(Runnable runnable){
            Platform.runLater(runnable);
        }

        @Override
        public void start(Stage stage) throws Exception {
            try {
                Properties properties = new Properties();
                properties.load(new FileInputStream(".bear/bear-fx.properties"));

                FXConf configurator = new FXConf(
                    "-VappCli.settingsFile=" + properties.get("bear-fx.settings"),
                    "-VappCli.propertiesFile=" + properties.get("bear-fx.properties")
                );

                configurator.bearFX = bearFX = new BearFX(this, configurator, properties);
                this.stage = stage;

                logger.info("creating fx appender...");

                //createFilter("DEBUG", "INFO", null, null)

                FXAppender fxAppDebug =
                    new FXAppender("fxAppDebug", ThresholdRangeFilter.createFilter("DEBUG", "INFO", null, null),
                        PatternLayout.createLayout("%highlight{%d{HH:mm:ss.S} %-5level %c{1.} - %msg%n}", null, null, null, null), true, bearFX);

                FXAppender fxAppInfo =
                    new FXAppender("fxAppInfo", ThresholdRangeFilter.createFilter("INFO", "FATAL", null, null),
                        PatternLayout.createLayout("%highlight{%d{HH:mm:ss.S} %-5level %c{1.} - %msg%n}", null, null, null, null), true, bearFX);

//                FXAppender fxAppInfo =
//                    new FXAppender("fxAppInfo", createFilter("info", null, null), PatternLayout.createLayout("%highlight{%d{HH:mm:ss.S} %-5level %c{1.} - %msg%n}", null, null, null, null), true, bearFX);


                addLog4jAppender("root", fxAppInfo, null, null);
                addLog4jAppender("fx", fxAppDebug, null, null);

                configurator.configure();

                final WebView webView = new WebView();
                webEngine = webView.getEngine();

                Scene scene = new Scene(webView);

                stage.setScene(scene);
//                stage.setFullScreen(true);

                setFullscreen(stage);
//                stage.setWidth(1200);
//                stage.setHeight(600);
                stage.show();

                webEngine.load(BearFX.class.getResource("/app/bear.html").toString());

                webEngine.setOnAlert(new EventHandler<WebEvent<String>>() {
                    @Override
                    public void handle(WebEvent<String> stringWebEvent) {
                        jsLogger.info(stringWebEvent.getData());
                    }
                });

                webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
                    @Override
                    public void changed(ObservableValue<? extends Worker.State> ov, Worker.State t, Worker.State t1) {
                        logger.debug("[JAVA INIT] setting...");

                        if (t1 == Worker.State.SUCCEEDED) {
                            JSObject window = (JSObject) webEngine.executeScript("window");

                            window.setMember("bearFX", bearFX);
                            window.setMember("OpenBean", OpenBean.INSTANCE);
                            window.setMember("Bindings", bindings);

                            logger.debug("[JAVA INIT] calling bindings JS initializer...");
                            webEngine.executeScript("Java.init(window);");
                            logger.debug("[JAVA INIT] calling app JS initializer...");
                            webEngine.executeScript("Java.initApp();");

                            bearFX.sendMessageToUI(new NewSessionConsoleEventToUI("status", randomId(), randomId()));

                            logger.error("[Loggers Diagnostics]");
                            LoggerFactory.getLogger(BearFX.class).debug("MUST NOT BE SEEN started the Bear - -1!");
                            LoggerFactory.getLogger("fx").info("started the Bear - 0!");
                            LoggerFactory.getLogger("fx").warn("started the Bear - 1!");
                            LoggerFactory.getLogger("root").warn("started the Bear - 2!");
                            LoggerFactory.getLogger(BearFX.class).warn("started the Bear - 3!");
                            LogManager.getLogger(BearFX.class).warn("started the Bear - 4!");
                            LoggerFactory.getLogger("fx").debug("started the Bear - 5!");
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void stop() throws Exception {
            bearFX.conf.getGlobal().shutdown();
        }

        public static void main(String[] args) throws Exception {
            launch(args);
        }
    }

    private static void addLog4jAppender(String loggerName, Appender appender, Level level, Filter filter) {
        try {
            org.apache.logging.log4j.core.Logger coreLogger
                = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(loggerName);
        LoggerContext context = coreLogger.getContext();
        org.apache.logging.log4j.core.config.BaseConfiguration configuration
            = (org.apache.logging.log4j.core.config.BaseConfiguration)context.getConfiguration();
            
            coreLogger.addAppender(appender);

            if("root".equals(loggerName)){
                for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                    loggerConfig.addAppender(appender, level, filter);
                }
            }

//            coreLogger
//                = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(loggerName);
//
//            Logger slf4jLogger = LoggerFactory.getLogger(loggerName);
//            
//            
//
//            if(slf4jLogger != null){
//                Field field = slf4jLogger.getClass().getDeclaredField("logger");
//                field.setAccessible(true);
//                AbstractLoggerWrapper loggerWrapper = (AbstractLoggerWrapper) field.get(slf4jLogger);
//
//                field = loggerWrapper.getClass().getDeclaredField("logger");
//                field.setAccessible(true);
//                field.set(loggerWrapper, coreLogger);
//            }

        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public static void setFullscreen(Stage stage) {
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();

        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    public void sendMessageToUI(EventToUI eventToUI) {
        bearFXApp.sendMessageToUI(eventToUI);
    }
}
