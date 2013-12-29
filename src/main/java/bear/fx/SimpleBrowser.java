package bear.fx;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class SimpleBrowser extends Pane {
    protected final WebView webView = new WebView();
    protected final WebEngine webEngine = webView.getEngine();

    protected boolean useFirebug;

    public WebView getWebView() {
        return webView;
    }

    public WebEngine getEngine() {
        return webView.getEngine();
    }

    public SimpleBrowser load(String location) {
        return load(location, null);
    }

    public SimpleBrowser load(String location, final Runnable onLoad) {
        webEngine.load(location);

        webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> ov, Worker.State t, Worker.State t1) {
                if (t1 == Worker.State.SUCCEEDED) {
                    if(useFirebug){
                        webEngine.executeScript("if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}");
                    }
                    if(onLoad != null){
                        onLoad.run();
                    }
                }
            }
        });

        return this;
    }

    public String getHTML() {
        return (String)webEngine.executeScript("document.getElementsByTagName('html')[0].innerHTML");
    }

    public SimpleBrowser useFirebug(boolean useFirebug) {
        this.useFirebug = useFirebug;
        return this;
    }

    public SimpleBrowser() {
        this(false);
    }

    public SimpleBrowser(boolean useFirebug) {
        this.useFirebug = useFirebug;

        getChildren().add(webView);

        webView.prefWidthProperty().bind(widthProperty());
        webView.prefHeightProperty().bind(heightProperty());
    }
}
