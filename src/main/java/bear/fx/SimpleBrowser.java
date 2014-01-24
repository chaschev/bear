package bear.fx;

import chaschev.util.Exceptions;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SimpleBrowser extends Pane {
    public static final Logger logger = LoggerFactory.getLogger(SimpleBrowser.class);
    private Random random = new Random();

    public static class Builder {
        boolean createWebView = true;
        boolean useFirebug;
        boolean useJQuery = true;

        public Builder createWebView(boolean createWebView) {
            this.createWebView = createWebView;
            return this;
        }

        public Builder useFirebug(boolean useFirebug) {
            this.useFirebug = useFirebug;
            return this;
        }

        public Builder useJQuery(boolean b) {
            this.useJQuery = b;
            return this;
        }

        public SimpleBrowser build() {
            return new SimpleBrowser(this);
        }
    }

    @Nullable
    protected final WebView webView;
    protected final WebEngine webEngine;

    protected final boolean useFirebug;
    protected final boolean useJQuery;

    public WebView getWebView() {
        return webView;
    }

    public WebEngine getEngine() {
        return webEngine;
    }

    public SimpleBrowser load(String location) {
        return load(location, null);
    }

    private final ConcurrentHashMap<Integer, Runnable> jQueryLoads = new ConcurrentHashMap<Integer, Runnable>();

    public void jQueryReady(int eventId){
        Runnable runnable = jQueryLoads.get(eventId);

        if(runnable!=null){
            runnable.run();
        }
    }

    public boolean waitFor(final String $predicate, int timeoutMs){
        long startedAt = System.currentTimeMillis();

        final int eventId = random.nextInt();

        final CountDownLatch latch = new CountDownLatch(1);

        jQueryLoads.put(eventId, new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        });

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                embedJQuery(eventId);
            }
        });

        try {
            if(!latch.await(timeoutMs, TimeUnit.MILLISECONDS)){
                return false;
            }

            int periodMs = timeoutMs / 20 + 2;

            while(true){
                long time = System.currentTimeMillis();

                if(time - startedAt > timeoutMs){
                    return false;
                }

                final CountDownLatch scriptLatch = new CountDownLatch(1);
                final boolean[] result = {false};

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        result[0] = (Boolean)webEngine.executeScript($predicate);
                        scriptLatch.countDown();
                    }
                });

                scriptLatch.await(periodMs, TimeUnit.MILLISECONDS);

                if(result[0]) return true;

                Thread.sleep(periodMs);
            }
        } catch (InterruptedException e) {
            throw Exceptions.runtime(e);
        }
    }


    public SimpleBrowser load(final String location, final Runnable onLoad) {
        logger.info("navigating to {}", location);

        webEngine.load(location);

        webEngine.setOnAlert(new EventHandler<WebEvent<String>>() {
            @Override
            public void handle(WebEvent<String> webEvent) {
                LoggerFactory.getLogger("wk-alert").info(webEvent.getData());
            }
        });

        final int eventId = random.nextInt();

        webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> ov, Worker.State t, Worker.State t1) {
//                logger.info("state: {},{} - for page {}", t, t1, location);

                if (t1 == Worker.State.SUCCEEDED) {
                    if(jQueryLoads.putIfAbsent(eventId, onLoad) != null){
                        return;
                    }

                    if(onLoad != null){
                        logger.info("registered event: {} for location {}, {}, {}", eventId, location, t, t1);
                        jQueryLoads.put(eventId, onLoad);
                    }

                    if (useFirebug) {
                        webEngine.executeScript("if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}");
                    }

                    if(useJQuery && !useFirebug){
                        embedJQuery(eventId);
                    }
                }
            }
        });

        return this;
    }

    protected void embedJQuery(int eventId) {
        JSObject jsWindow = (JSObject) webEngine.executeScript("window");

        jsWindow.setMember("simpleBrowser", SimpleBrowser.this);

        String script = "" +
            "if(typeof jQuery == 'undefined'){" +
            "script = document.createElement('script');\n" +
            "\n" +
            "script.onload = function() {\n" +
            "    try{\n" +
//                            "      alert('jQuery is available now: ' + jQuery);\n" +
            "      window.simpleBrowser.jQueryReady(" + eventId + ");\n" +
            "    } catch(e){\n" +
            "       alert(e);\n" +
            "    }\n"+
            "};\n" +
            "var head = document.getElementsByTagName(\"head\")[0];\n" +
            "\n" +
            "script.type = 'text/javascript';\n" +
            "script.src = 'https://ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js';\n" +
            "\n" +
            "head.appendChild(script);" +
            "}else{" +
            " window.simpleBrowser.jQueryReady(" + eventId + ");\n" +
            "}";

        webEngine.executeScript(script);
    }

    public String getHTML() {
        return (String) webEngine.executeScript("document.getElementsByTagName('html')[0].innerHTML");
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public SimpleBrowser(Builder builder) {
        this.useFirebug = builder.useFirebug;
        this.useJQuery = builder.useJQuery;

        if (builder.createWebView) {
            webView = new WebView();
            webEngine = webView.getEngine();

            getChildren().add(webView);

            webView.prefWidthProperty().bind(widthProperty());
            webView.prefHeightProperty().bind(heightProperty());
        } else {
            webView = null;
            webEngine = new WebEngine();
        }
    }
}
