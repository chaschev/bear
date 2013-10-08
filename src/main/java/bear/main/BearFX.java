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

import chaschev.js.Bindings;
import chaschev.js.ExceptionWrapper;
import chaschev.lang.OpenBean;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearFX {
    public final BearCommandLineConfigurator conf;
    public final Facade facade = new Facade();

    public class Facade{
        public Object call(String delegate, String method, Object... params) throws IOException {
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

    public BearFX(BearCommandLineConfigurator conf) {
        this.conf = conf;
    }

    public static class TestBindingsApp extends Application {
        private BearCommandLineConfigurator configurator;

        private WebEngine webEngine;
        private BearFX bearFX;

        @Override
        public void start(Stage stage) throws Exception {
            try {
                Properties properties = new Properties();
                properties.load(new FileInputStream(".bear/settings.properties"));

                BearCommandLineConfigurator configurator = new BearCommandLineConfigurator(
                    "--script=" + properties.get("bear-fx.script") + " " +
                        "--settings=" + properties.get("bear-fx.settings") + " "
                ).setProperties(properties)
                 .configure();

                bearFX = new BearFX(configurator);

                //////////
                //////////
                //////////

                final WebView webView = new WebView();
                webEngine = webView.getEngine();

                Scene scene = new Scene(webView);

                stage.setScene(scene);
                stage.setWidth(1200);
                stage.setHeight(600);
                stage.show();

                webEngine.load(BearFX.class.getResource("/app/bear.html").toString());

                webEngine.setOnAlert(new EventHandler<WebEvent<String>>() {
                    @Override
                    public void handle(WebEvent<String> stringWebEvent) {
                        System.out.println("alert: " + stringWebEvent.getData());
                    }
                });

                webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
                    @Override
                    public void changed(ObservableValue<? extends Worker.State> ov, Worker.State t, Worker.State t1) {
                        System.out.println("[JAVA INIT] setting...");
                        if (t1 == Worker.State.SUCCEEDED) {
                            System.out.println("ok");
                            JSObject window = (JSObject) webEngine.executeScript("window");
                            window.setMember("bearFX", bearFX);
                            window.setMember("OpenBean", OpenBean.INSTANCE);
                            window.setMember("Bindings", new Bindings());

                            System.out.println("[JAVA INIT] calling bindings JS initializer...");
                            webEngine.executeScript("Java.init(window);");
                            System.out.println("[JAVA INIT] calling app JS initializer...");
                            webEngine.executeScript("Java.initApp();");
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void main(String[] args) throws Exception {
            launch(args);
        }
    }

}
