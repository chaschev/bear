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

import java.util.Arrays;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TestOverloadingApp extends Application {

    private WebEngine webEngine;

    @Override
    public void start(Stage stage) throws Exception {
        final WebView webView = new WebView();
        webEngine = webView.getEngine();

        Scene scene = new Scene(webView);

        stage.setScene(scene);
        stage.setWidth(1200);
        stage.setHeight(600);
        stage.show();

        // this is a proper way to load a page from your resources
        // if your testOverloading.html references an image with <img src="images/test.jpg">
        // then your test.jpg must be placed in /javafx/overloading1/images/test.jpg
        // which is quite straight-forward
        webEngine.load(TestOverloadingApp.class.getResource("/javafx/overloading1/testOverloading.html").toURI().toURL().toString());

        webEngine.setOnAlert(new EventHandler<WebEvent<String>>() {
            @Override
            public void handle(WebEvent<String> stringWebEvent) {
                // this is a simple way to debug your app - call alert('this will into your Java log') on the JS side
                System.out.println("alert: " + stringWebEvent.getData());
            }
        });

        webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> ov, Worker.State t, Worker.State t1) {
                System.out.println("[JAVA INIT] setting...");
                if (t1 == Worker.State.SUCCEEDED) {
                    // get the window object is a global variable in JS
                    JSObject window = (JSObject) webEngine.executeScript("window");

                    // bind our Java objects as fields in the window object
                    // in JS this would look like
                    // window.foo = new Foo()
                    window.setMember("fooWhichIsOK", new FooWhichIsOk());
                    window.setMember("foo", new Foo());
                }
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class FooWhichIsOk {
        public void fooDiffName() {
            System.out.println("FooWhichIsOk!");
        }

        public void foo(String s) {
            System.out.println("FooWhichIsOk, " + s + "!!");
        }

        public void array(Object[] params) {
            System.out.println("Arrays.asList(params) = " + Arrays.asList(params));
        }
    }

    public static class Foo {
        public void foo() {
            System.out.println("Foo!");
        }

        public void foo(String s) {
            System.out.println("Foo, " + s + "!!");
        }
    }
}
