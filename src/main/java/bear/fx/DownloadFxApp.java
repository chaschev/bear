/*
 * Copyright (C) 2014 Andrey Chaschev.
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

package bear.fx;

import chaschev.io.FileUtils;
import chaschev.lang.LangUtils;
import chaschev.util.Exceptions;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import com.google.common.io.Resources;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.VBoxBuilder;
import javafx.stage.Stage;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class DownloadFxApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(DownloadFxApp.class);

    public static volatile String oracleUser;
    public static volatile String oraclePassword;

    public final CountDownLatch downloadLatch = new CountDownLatch(1);
    private static final CountDownLatch appStartedLatch = new CountDownLatch(1);

    public final AtomicReference<DownloadResult> downloadResult = new AtomicReference<DownloadResult>();
    protected static final AtomicReference<DownloadFxApp> instance = new AtomicReference<DownloadFxApp>();

    public static volatile String version = "6u39";
    public static volatile boolean miniMode = false;
    public static volatile File tempDestDir = new File(".");

    public static void launchUI() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                new DownloadFxApp().createScene(new javafx.stage.Stage());
            }
        });
    }

    public static class DownloadResult {
        public File file;

        public String message;
        public boolean ok;

        public DownloadResult(File file, String message, boolean ok) {
            this.file = file;
            this.message = message;
            this.ok = ok;
        }
    }

    public static void awaitStart(){
        try {
            appStartedLatch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static DownloadFxApp getInstance() {
        awaitStart();

        return instance.get();
    }

    static void tryFind(final SimpleBrowser browser, String archiveUrl, final WhenDone whenDone) {
        browser.load(archiveUrl, new Runnable() {
            @Override
            public void run() {
                try {
                    Boolean aBoolean = (Boolean) browser.getEngine().executeScript(downloadJDKJs() + "\n " +
                        "downloadIfFound('" + version + "', true, 'linux');");

                    if(aBoolean){
                        whenDone.whenDone(true);
                    }else{
                        whenDone.whenDone(false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static String downloadJDKJs() {
        try {
            return Resources.toString(DownloadFxApp.class.getResource("downloadJDK.js"), Charsets.UTF_8);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        createScene(stage);
    }

    public void createScene(Stage stage) {
        try {
            stage.setTitle("Downloading JDK " + version + "...");

            instance.set(this);
            appStartedLatch.countDown();

            final SimpleBrowser browser = SimpleBrowser.newBuilder()
                .useFirebug(false)
                .useJQuery(true)
                .createWebView(!miniMode)
                .build()
                ;

            final ProgressBar progressBar = new ProgressBar(0);
            final Label progressLabel = new Label("Retrieving a link...");

            VBox vBox = VBoxBuilder.create()
                .children(progressLabel, progressBar, browser)
                .fillWidth(true)
                .build();

            Scene scene = new Scene(vBox);

            stage.setScene(scene);

            if(miniMode){
                stage.setWidth(300);
            }else{
                stage.setWidth(1024);
                stage.setHeight(768);
            }

            stage.show();

            VBox.setVgrow(browser, Priority.ALWAYS);

            /**
             *
             location changed to: http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase7-521261.html?
             location changed to: http://download.oracle.com/otn/java/jdk/7u45-b18/jdk-7u45-linux-x64.tar.gz
             location changed to: https://edelivery.oracle.com/akam/otn/java/jdk/7u45-b18/jdk-7u45-linux-x64.tar.gz
             location changed to: https://login.oracle.com/pls/orasso/orasso.wwsso_app_admin.ls_login?Site2pstoreToken=v1.2~CA55CD32~750C6EFBC9B3CB198B2ADFE87BDD4DEB60E0218858C8BFE85DCCC65865D0E810E845839B422974847E1D489D3AF25FDC9574400197F9190C389876C1EC683A6006A06F7F05D41C94455B8354559F5699F5D0EF102F26FE905E77D40487455F7829501E3A783E1354EB0B8F05B828D0FC3BA22C62D3576883850E0B99849309B0C26F286E5650F63E9C6A7C376165C9A3EED86BF2FA0FAEE3D1F7F2957F5FBD5035AF0A3522E534141FE38DFDD55C4F7F517F9E81336C993BB76512C0D30A5B5C5FD82ED1C10E9D27284B6B1633E4B7B9FA5C2E38D9C5E3845C18C009E294E881FD8B654B67050958E57F0DC20885D6FA87A59FAA7564F94F
             location changed to: https://login.oracle.com/mysso/signon.jsp
             location changed to: https://login.oracle.com/oam/server/sso/auth_cred_submit
             location changed to: https://edelivery.oracle.com/osso_login_success?urlc=v1.2%7E30E69346FE17F27D4F83121B0B8EC362E0B315901364AAA7D6F0B7A05CD8AA31802F5A69D70C708F34C64B65D233922B57D3C31839E82CE78E5C8DA55D729DD339893285D21A8E8B1AE8557C9240D6E33C9965956E136F4CB093779F97AF67C3DB8FF19FF2A638296BD0AA81A7801904AC5607F0568B6CEAF7ED9FCE4B7BEA80071617E4B2779F60F0C76A89F7D195965D2F003F9EDD2A1ADFD264C1C4C7F921010B08D3846CEC9524237A9337B6B0BC433BB17993A670B6C913EB4CFDC217A753F9E2943DE0CBDC41D4705AC67C2B96A4892C65F5450B146939B0EBFDF098680BBBE1F13356460C95A23D8D198D1C6762E45E62F120E32C2549E6263071DA84F8321370D2410CCA93E9A071A02ED6EB40BF40EDFC6F65AC7BA73CDB06DF4265455419D9185A6256FFE41A7FF54042374D09F5C720F3104B2EAC924778482D4BE855A45B2636CE91C7D947FF1F764674CE0E42FFCCFE411AABFE07EA0E96838AFEA263D2D5A405BD
             location changed to: https://edelivery.oracle.com/akam/otn/java/jdk/7u45-b18/jdk-7u45-linux-x64.tar.gz
             location changed to: http://download.oracle.com/otn/java/jdk/7u45-b18/jdk-7u45-linux-x64.tar.gz?AuthParam=1390405890_f9186a44471784229268632878dd89e4

             */

            browser.getEngine().locationProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observableValue, String oldLoc, final String uri) {
                    logger.info("change: {}", uri);

                    if(uri.contains("signon.jsp")){
                        browser.getEngine().executeScript("" +
                            "alert(document);\n" +
                            "alert(document.getElementById('sso_username'));\n"
                        );

                        new Thread("signon.jsp waiter"){
                            @Override
                            public void run() {
                                setStatus("waiting for the login form...");

                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    throw Exceptions.runtime(e);
                                }

                                browser.waitFor("$('#sso_username').length > 0", 10000);

                                System.out.println("I see it all, I see it now!");

                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        browser.getEngine().executeScript("" +
                                            "alert(document.getElementById('sso_username'));\n" +
                                            "alert($('#sso_username').val('" + oracleUser + "'));\n" +
                                            "alert($('#ssopassword').val('" + oraclePassword + "'));\n" +
                                            downloadJDKJs() + "\n" +
                                            "clickIt($('.sf-btnarea a'))"
                                        );
                                    }
                                });
                            }
                        }.start();
                    }

                    if(uri.contains("download.oracle") && uri.contains("?")){
                        //will be here after
                        // clicking accept license and link -> * not logged in * -> here -> download -> redirect to login
                        // download -> fill form -> * logged in * -> here -> download
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    DefaultHttpClient httpClient = new DefaultHttpClient();

                                    HttpGet httppost = new HttpGet(uri);

                                    HttpResponse response = httpClient.execute(httppost);

                                    int code = response.getStatusLine().getStatusCode();

                                    if (code != 200) {
                                        System.out.println(IOUtils.toString(response.getEntity().getContent()));
                                        throw new RuntimeException("failed to download: " + uri);
                                    }

                                    final File file = new File(tempDestDir, StringUtils.substringBefore(FilenameUtils.getName(uri), "?"));
                                    HttpEntity entity = response.getEntity();

                                    final long length = entity.getContentLength();

                                    final CountingOutputStream os = new CountingOutputStream(new FileOutputStream(file));

                                    System.out.printf("Downloading %s to %s...%n", uri, file);

                                    Thread progressThread = new Thread(new Runnable() {
                                        double lastProgress;

                                        @Override
                                        public void run() {
                                            while (!Thread.currentThread().isInterrupted()) {
                                                long copied = os.getCount();

                                                double progress = copied * 100D / length;

                                                if (progress != lastProgress) {
                                                    final String s = String.format("%s: %s/%s %s%%",
                                                        file.getName(),
                                                        FileUtils.humanReadableByteCount(copied, false, false),
                                                        FileUtils.humanReadableByteCount(length, false, true),
                                                        LangUtils.toConciseString(progress, 1));

                                                    setStatus(s);

                                                    System.out.print("\r" + s);
                                                }

                                                lastProgress = progress;
                                                progressBar.setProgress(copied * 1D / length);

//                                                progressProp.set(progress);

                                                try {
                                                    Thread.sleep(500);
                                                } catch (InterruptedException e) {
                                                    break;
                                                }
                                            }
                                        }
                                    }, "progressThread");

                                    progressThread.start();

                                    ByteStreams.copy(entity.getContent(), os);

                                    progressThread.interrupt();

                                    System.out.println("Download complete.");

                                    downloadResult.set(new DownloadResult(file, "", true));
                                    downloadLatch.countDown();
                                } catch (Exception e) {
                                    LoggerFactory.getLogger("log").warn("", e);
                                    downloadResult.set(new DownloadResult(null, e.getMessage(), false));
                                    throw Exceptions.runtime(e);
                                }
                            }
                        }, "fx-downloader");

                        thread.start();
                    }
                }

                public void setStatus(final String s) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressLabel.setText(s);
                        }
                    });
                }
            });

            // links from http://www.oracle.com/technetwork/java/archive-139210.html

            Map<Integer, String> archiveLinksMap = new HashMap<Integer, String>();

            archiveLinksMap.put(5, "http://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-javase5-419410.html");
            archiveLinksMap.put(6, "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase6-419409.html");
            archiveLinksMap.put(7, "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase7-521261.html");

            Map<Integer, String> latestLinksMap = new HashMap<Integer, String>();

            latestLinksMap.put(7, "http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html");

            String archiveUrl = null;
            String latestUrl = null;

            char ch = version.charAt(0);

            switch (ch){
                case '7':
                case '6':
                case '5':
                    latestUrl = latestLinksMap.get(ch - '0');
                    archiveUrl = archiveLinksMap.get(ch - '0');
                    break;
                default:
                    archiveUrl = null;
            }

            if(latestUrl != null){
                final String finalArchiveUrl = archiveUrl;
                tryFind(browser, latestUrl, new WhenDone() {
                    @Override
                    public void whenDone(boolean found) {
                        tryArchiveLink(found, finalArchiveUrl, browser);
                    }
                });
            }else{
                tryArchiveLink(false, archiveUrl, browser);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void tryArchiveLink(boolean found, String finalArchiveUrl, SimpleBrowser browser) {
        if (!found && finalArchiveUrl != null) {
            tryFind(browser, finalArchiveUrl, new WhenDone() {
                @Override
                public void whenDone(boolean found) {
                    if (found) {
                        System.out.println("Will be redirected to login page...");
                    } else {
                        downloadResult.set(new DownloadResult(null, "didn't find a link", false));
                        downloadLatch.countDown();
                    }
                }
            });
        } else {
            System.out.println("Download started...");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static interface WhenDone{
        void whenDone(boolean found);
    }

    public DownloadFxApp awaitDownload(long timeout, TimeUnit unit) throws InterruptedException {
        downloadLatch.await(timeout, unit);
        return this;
    }
}
