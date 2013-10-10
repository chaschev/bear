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

package javafx.overloading1;

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

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TestOverloading {
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

                webEngine.load(TestOverloading.class.getResource("/javafx/overloading1/testOverloading.html").toURI().toURL().toString());

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
                            JSObject window = (JSObject) webEngine.executeScript("window");

                            window.setMember("fooWhichIsOK", new FooWhichIsOk());
                            window.setMember("foo", new Foo());
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

        public static class FooWhichIsOk {
            public void fooDiffName(){
                System.out.println("FooWhichIsOk!");
            }

            public void foo(String s){
                System.out.println("FooWhichIsOk, " + s + "!!");
            }
        }

        public static class Foo {
            public void foo(){
                System.out.println("Foo!");
            }

            public void foo(String s){
                System.out.println("Foo, " + s + "!!");
            }
        }
    }

}
