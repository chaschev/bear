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
import chaschev.js.Bindings;
import chaschev.js.ExceptionWrapper;
import chaschev.json.JacksonMapper;
import chaschev.json.Mapper;
import chaschev.lang.OpenBean;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

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
                        logger.info("[JAVA INIT] setting...");

                        if (t1 == Worker.State.SUCCEEDED) {
                            logger.info("ok");
                            JSObject window = (JSObject) webEngine.executeScript("window");
                            window.setMember("bearFX", bearFX);
                            window.setMember("OpenBean", OpenBean.INSTANCE);
                            window.setMember("Bindings", bindings);

                            logger.info("[JAVA INIT] calling bindings JS initializer...");
                            webEngine.executeScript("Java.init(window);");
                            logger.info("[JAVA INIT] calling app JS initializer...");
                            webEngine.executeScript("Java.initApp();");
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
