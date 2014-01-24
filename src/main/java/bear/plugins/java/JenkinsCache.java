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

package bear.plugins.java;

import chaschev.lang.LangUtils;
import chaschev.util.Exceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gistlabs.mechanize.MechanizeAgent;
import com.gistlabs.mechanize.cookie.Cookie;
import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class JenkinsCache {
    public int version;
    public List<JDKVersion> data;

    public Optional<JDKFile> findJDK(String version) {
        return findJDK(version, true, true);
    }

    public static Optional<JDKFile> load(File jenkinsCache, String jenkinsUri, String jdkVersion) {
        JenkinsCache cache = load(jenkinsCache, jenkinsUri, false);

        Optional<JDKFile> jdk = cache.findJDK(jdkVersion);

        if (jdk.isPresent()) return jdk;

        return load(jenkinsCache, jenkinsUri, true).findJDK(jdkVersion);
    }
    public static File download2(String jdkVersion, File jenkinsCache, File tempDestDir, String jenkinsUri, String user, String pass) {
        try {
            Optional<JDKFile> optional = load(jenkinsCache, jenkinsUri, jdkVersion);

            if (!optional.isPresent()) {
                throw new RuntimeException("could not find: " + jdkVersion);
            }

            String uri = optional.get().filepath;



//                agent.get()

//                agent.get()

            SSLContext sslContext = SSLContext.getInstance("TLSv1");

            sslContext.init(null, new TrustManager[] { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    System.out.println("getAcceptedIssuers =============");
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs,
                                               String authType) {
                    System.out.println("checkClientTrusted =============");
                }

                public void checkServerTrusted(X509Certificate[] certs,
                                               String authType) {
                    System.out.println("checkServerTrusted =============");
                }
            } }, new SecureRandom());

            SSLSocketFactory sf = new SSLSocketFactory(sslContext);

            Scheme httpsScheme = new Scheme("https", 443, sf);
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(httpsScheme);

            DefaultHttpClient httpClient = new DefaultHttpClient(new PoolingClientConnectionManager(schemeRegistry));

            MechanizeAgent agent = new MechanizeAgent();
            Cookie cookie2 = agent.cookies().addNewCookie("gpw_e24", ".", "oracle.com");
            cookie2.getHttpCookie().setPath("/");
            cookie2.getHttpCookie().setSecure(false);



            CookieStore cookieStore = new BasicCookieStore();
            BasicClientCookie cookie = new BasicClientCookie("gpw_e24", ".");
            cookie.setDomain("oracle.com");
            cookie.setPath("/");
            cookie.setSecure(true);

            cookieStore.addCookie(cookie);

            httpClient.setCookieStore(cookieStore);

            HttpPost httppost = new HttpPost("https://login.oracle.com");

            httppost.setHeader("Authorization", "Basic " + new String(Base64.encodeBase64((user + ":" + pass).getBytes()), "UTF-8"));

            HttpResponse response = httpClient.execute(httppost);

            int code = response.getStatusLine().getStatusCode();

            if (code != 302) {
                System.out.println(IOUtils.toString(response.getEntity().getContent()));
                throw new RuntimeException("unable to auth: " + code);
            }

//                EntityUtils.consumeQuietly(response.getEntity());

            httppost = new HttpPost(uri);

            response = httpClient.execute(httppost);

            code = response.getStatusLine().getStatusCode();

            if (code != 302) {
                System.out.println(IOUtils.toString(response.getEntity().getContent()));
                throw new RuntimeException("to download: " + uri);
            }

            File file = new File(tempDestDir, optional.get().name);
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
                            System.out.printf("\rProgress: %s%%", LangUtils.toConciseString(progress, 1));
                        }

                        lastProgress = progress;

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

            return file;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public static File download(String jdkVersion, File jenkinsCache, File tempDestDir, String jenkinsUri, String user, String pass) {
        try {
            Optional<JDKFile> optional = load(jenkinsCache, jenkinsUri, jdkVersion);

            if (!optional.isPresent()) {
                throw new RuntimeException("could not find: " + jdkVersion);
            }

            String uri = optional.get().filepath;

            SSLContext sslContext = SSLContext.getInstance("TLSv1");

            sslContext.init(null, new TrustManager[] { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    System.out.println("getAcceptedIssuers =============");
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs,
                                               String authType) {
                    System.out.println("checkClientTrusted =============");
                }

                public void checkServerTrusted(X509Certificate[] certs,
                                               String authType) {
                    System.out.println("checkServerTrusted =============");
                }
            } }, new SecureRandom());

            SSLSocketFactory sf = new SSLSocketFactory(sslContext);

            Scheme httpsScheme = new Scheme("https", 443, sf);
            SchemeRegistry schemeRegistry = new SchemeRegistry();

            Scheme httpScheme = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());

            schemeRegistry.register(httpsScheme);
            schemeRegistry.register(httpScheme);

            DefaultHttpClient httpClient = new DefaultHttpClient(new PoolingClientConnectionManager(schemeRegistry));

            CookieStore cookieStore = new BasicCookieStore();
            BasicClientCookie cookie = new BasicClientCookie("gpw_e24", ".");
            cookie.setDomain("oracle.com");
            cookie.setPath("/");
            cookie.setSecure(true);

            cookieStore.addCookie(cookie);

            httpClient.setCookieStore(cookieStore);

            HttpPost httppost = new HttpPost("https://login.oracle.com");

            httppost.setHeader("Authorization", "Basic " + new String(Base64.encodeBase64((user + ":" + pass).getBytes()), "UTF-8"));

            HttpResponse response = httpClient.execute(httppost);

            int code = response.getStatusLine().getStatusCode();

            if (code != 302) {
                System.out.println(IOUtils.toString(response.getEntity().getContent()));
                throw new RuntimeException("unable to auth: " + code);
            }

            // closes the single connection
//                EntityUtils.consumeQuietly(response.getEntity());

            httppost = new HttpPost(uri);

            httppost.setHeader("Authorization", "Basic " + new String(Base64.encodeBase64((user + ":" + pass).getBytes()), "UTF-8"));

            response = httpClient.execute(httppost);

            code = response.getStatusLine().getStatusCode();

            if (code != 302) {
                System.out.println(IOUtils.toString(response.getEntity().getContent()));
                throw new RuntimeException("to download: " + uri);
            }

            File file = new File(tempDestDir, optional.get().name);
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
                            System.out.printf("\rProgress: %s%%", LangUtils.toConciseString(progress, 1));
                        }

                        lastProgress = progress;

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

            return file;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public static JenkinsCache load(File file, String fallbackUri, boolean forceUpdate) {
        try {
            if (!file.exists() || forceUpdate) {
                HttpClient httpClient = new DefaultHttpClient();
                HttpGet httpget = new HttpGet(fallbackUri);

                HttpResponse response = httpClient.execute(httpget);

                int code = response.getStatusLine().getStatusCode();

                if (code != 200) {
                    throw new RuntimeException("unable to download " + fallbackUri +
                        ", error code: " + code);
                }

                String body = IOUtils.toString(response.getEntity().getContent());

                body = body.substring(body.indexOf('{'), body.lastIndexOf(')'));

                FileUtils.writeStringToFile(file, body);

                return new ObjectMapper().readValue(body, JenkinsCache.class);
            }

            return new ObjectMapper().readValue(file, JenkinsCache.class);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public Optional<JDKFile> findJDK(String version, boolean linux, boolean x64) {
        for (JDKVersion jdkVersion : data) {
            for (JDKReleases release : jdkVersion.releases) {
                for (JDKFile file : release.files) {
                    if (file.name.contains(version) &&
                        file.name.contains(linux ? "linux" : "windows")) {
                        if (x64) {
                            if (file.name.contains("x64") /*|| file.name.contains("ia64")*/) {
                                return Optional.of(file);
                            }
                        } else {
                            if (file.name.contains("i586")) {
                                return Optional.of(file);
                            }
                        }
                    }
                }
            }
        }

        return Optional.absent();
    }

    public static class JDKVersion {
        public String name;
        public List<JDKReleases> releases;
    }

    public static class JDKReleases {
        public List<JDKFile> files;
        public String licpath;
        public String path;  //rare
        public String lictitle;
        public String name;
        public String title;
    }

    public static class JDKFile {
        public String filepath;
        public String name;
        public String title;

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("JDKFile{");
            sb.append("filepath='").append(filepath).append('\'');
            sb.append(", name='").append(name).append('\'');
            sb.append(", title='").append(title).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}
