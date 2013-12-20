package bear.maven;


import chaschev.util.Exceptions;
import com.google.common.base.Optional;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.substringAfter;

/**
 * A helper to boot the repository system and a repository system session.
 */
public class MavenBooter {
    private static final Logger logger = LoggerFactory.getLogger(MavenBooter.class);

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.*)::(.+)");

    public static final File M2_REPO_DIR = new File(SystemUtils.getUserHome(), ".m2/repository");

    protected final List<RemoteRepository> repositories = new ArrayList<RemoteRepository>();
    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session;

    public MavenBooter(Properties properties) {
        parseRepositories(properties);

        system = MavenBooter.newRepositorySystem();
        session = MavenBooter.newSession(system, MavenBooter.M2_REPO_DIR);
    }

    public static RepositorySystem newRepositorySystem() {
        return ManualRepositorySystemFactory.newRepositorySystem();

//        return new DefaultRepositorySystem();
    }


    public static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();
//        return new RemoteRepository( "central", "default", "http://repo1.maven.org/maven2/" );
    }

    public static RemoteRepository newSonatypeRepository() {
        return new RemoteRepository.Builder("sonatype-snapshots", "default", "https://oss.sonatype.org/content/repositories/snapshots/").build();
//        return new RemoteRepository("sonatype-snapshots", "default", "https://oss.sonatype.org/content/repositories/snapshots/");
    }

    public static DefaultRepositorySystemSession newSession(RepositorySystem system, File repositoryDir) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository(repositoryDir);

        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

//        session.setTransferListener( new ConsoleTransferListener() );
//        session.setRepositoryListener( new ConsoleRepositoryListener() );

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        return session;
    }

    public Optional<ClassLoader> loadArtifacts(Properties properties){
        Set<Map.Entry<String, String>> entries = (Set) properties.entrySet();

        boolean hasErrors = false;

        List<URL> urls = new ArrayList<URL>();

        for (Map.Entry<String, String> entry : entries) {
            String key = entry.getKey();

            if(!key.startsWith("dep.")) continue;

            try {
                DependencyResult dependencyResult = resolveArtifact(new DefaultArtifact(entry.getValue()));

                List<ArtifactResult> results = dependencyResult.getArtifactResults();

                for (ArtifactResult result : results) {
                    urls.add(result.getArtifact().getFile().toURI().toURL());
                }
            } catch (ArtifactResolutionException e) {
                logger.error(e.toString());
                hasErrors = true;
            } catch (MalformedURLException e) {
                throw Exceptions.runtime(e);
            }
        }

        if(hasErrors){
            System.out.println("Unable to resolve artifacts, exiting.");
            System.exit(-1);
        }

        if(urls.isEmpty()) return Optional.absent();

        return Optional.of((ClassLoader) new URLClassLoader(urls.toArray(new URL[urls.size()])));
    }

    public DependencyResult resolveArtifact(Artifact artifact) throws ArtifactResolutionException {
        try {
            logger.debug("resolving artifact {}...", artifact);

            ArtifactRequest artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact(artifact);

            artifactRequest.setRepositories(repositories);

            DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
            collectRequest.setRepositories(repositories);

            DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, classpathFilter );

            return    system.resolveDependencies( session, dependencyRequest );
        } catch (DependencyResolutionException e) {
            throw Exceptions.runtime(e);
        }
    }

    protected final void parseRepositories(Properties properties){
        RepositoryParser parser = new RepositoryParser();

        boolean hasCentral = false;

        Set<Map.Entry<String, String>> entries = (Set) properties.entrySet();

        for (Map.Entry<String, String> entry : entries) {
            String key = entry.getKey();
            
            if(!key.startsWith("repo.")) continue;

            RemoteRepository repo = parser.parseRepository(substringAfter(key, "repo."), entry.getValue());

            if(repo.getHost().equals("repo1.maven.org")) hasCentral = true;

            repositories.add(repo);
        }

        if(!hasCentral){
            repositories.add(new RemoteRepository.Builder(null, "default", "http://repo1.maven.org/maven2/").build());
        }
    }

    public static class RepositoryParser {

        public RepositoryParser() {
        }

        public RemoteRepository parseRepository(String id, String repo) {
            // if it's a simple url
            String layout = "default";
            String url = repo;

            // if it's an extended repo URL of the form id::layout::url
            if (repo.contains("::")) {
                Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(repo);

                if (!matcher.matches()) {
                    throw new IllegalArgumentException("Invalid syntax for repository " + repo +
                        ". Use \"layout::url\" or just \"URL\".");
                }

                if (!StringUtils.isEmpty(matcher.group(1))) {
                    layout = matcher.group(1).trim();
                }

                url = matcher.group(2).trim();
            } else {
                url = repo;
            }

            RemoteRepository.Builder builder = new RemoteRepository.Builder(id, layout, url);

            if (url.contains("snapshot")) {
                builder.setPolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS,
                    RepositoryPolicy.CHECKSUM_POLICY_WARN));
            }

            return builder.build();
        }
    }
}
