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

package bear.smoke;

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

import java.io.File;
import java.util.ArrayList;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TestBindings {
    public static class TestBindingsApp extends Application {

        private WebEngine webEngine;

        @Override
        public void start(Stage stage) throws Exception {
            try {
                final WebView webView = new WebView();
                webEngine = webView.getEngine();

                Scene scene = new Scene(webView);

                stage.setScene(scene);
                stage.setWidth(1200);
                stage.setHeight(600);
                stage.show();

                webEngine.load(new File("src/main/resources/app/testBindings.html").toURI().toURL().toString());

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
                            window.setMember("app", new JavaApp().enableFirebug());
                            window.setMember("OpenBean", OpenBean.INSTANCE);
                            window.setMember("Bindings", new Bindings());

                            System.out.println("[JAVA INIT] calling JS initializer...");
                            webEngine.executeScript("Java.init(window)");
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void main(String[] args) {
            launch(args);
        }

        public static class Bindings{
            public ArrayList newArrayList(){
                return new ArrayList();
            }

            public Object[] newObjectArray(int size){
                return new Object[size];
            }

            public Object newInstance(String className, Object... params) {
                return OpenBean.newByClass(className, params);
            }

            public Object newInstance(String className, boolean strictly, Object... params) {
                return OpenBean.newByClass(className, strictly, params);
            }
        }

        public class JavaApp {
            private String s = "test s";

            public String getS() {
                return s;
            }

            public void setS(String s) {
                this.s = s;
            }

            public void onClick1(Object t) {
                System.out.println("Clicked");
                System.out.printf("%s%n", t);
                webEngine.executeScript("Object.keys({'s':'hi'})");
                System.out.println(((JSObject)t).call("keys"));

                System.out.flush();
            }

            public void onClick2() {
                System.out.println("onClick2");
                webEngine.executeScript("");
            }

            public JavaApp enableFirebug() {
                System.out.println("enabling firebug!");

                webEngine.executeScript("if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}");

                return this;
            }
        }
    }
}
