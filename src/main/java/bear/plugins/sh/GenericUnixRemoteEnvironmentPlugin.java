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

package bear.plugins.sh;

import bear.annotations.Shell;
import bear.core.*;
import bear.session.Address;
import bear.session.SshAddress;
import bear.task.Task;
import chaschev.util.CatchyCallable;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Shell("ssh")
public class GenericUnixRemoteEnvironmentPlugin extends SystemEnvironmentPlugin {
    private static final Logger logger = LoggerFactory.getLogger(RemoteConsole.class);

    public GenericUnixRemoteEnvironmentPlugin(GlobalContext global) {
        super(global, "remote unix plugin");


        this.shell = new ShShellMode(this, cmdAnnotation());
    }

    public static class RemoteConsole extends AbstractConsole {
        Session.Command command;

        public RemoteConsole(Closeable shutdownTrigger, Session.Command command, Listener listener) {
            super(listener, shutdownTrigger);
            this.command = command;

            super.addInputStream(command.getInputStream());
            super.addInputStream(command.getErrorStream(), true);
            super.setOut(command.getOutputStream());
        }
    }

    @Override
    public SystemSession newSession(SessionContext $, Task parent) {
        connect();

        return new RemoteSystemSession(this, parent, $);
    }

    public static class SshSession {
        private SSHClient ssh;
        private Future<SSHClient> sshFuture;

        SshAddress sshAddress;

        boolean reuseSession = false;
        private Session session;

        public SshSession(final SshAddress sshAddress, GlobalContext global) {
            this.sshAddress = sshAddress;

            sshFuture = global.sessionsExecutor.submit(new CatchyCallable<SSHClient>(new Callable<SSHClient>() {
                @Override
                public SSHClient call() throws Exception {
                    try {
                        logger.info("connecting to {}", sshAddress.address);

                        SSHClient ssh = new SSHClient();
                        ssh.loadKnownHosts(new File(SystemUtils.getUserHome(), ".ssh/known_hosts"));
                        ssh.connect(sshAddress.address);
                        ssh.authPassword(sshAddress.username, sshAddress.password);

                        return ssh;
                    } catch (Exception e) {
                        final String fingerprint = StringUtils.substringBetween(
                            e.toString(), "fingerprint `", "`");

                        SSHClient ssh = new SSHClient();

                        ssh.loadKnownHosts(new File(SystemUtils.getUserHome(), ".ssh/known_hosts"));
                        ssh.addHostKeyVerifier(fingerprint);
                        ssh.connect(sshAddress.address);
                        ssh.authPassword(sshAddress.username, sshAddress.password);

                        return ssh;
                    }
                }
            }));
        }

        public synchronized SSHClient getSsh() {
            if (ssh == null) {
                try {
                    ssh = sshFuture.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            return ssh;
        }

        abstract static class WithLowLevelSession {
            public Bear bear;
            public String text;

            protected WithLowLevelSession(Bear bear) {
                this.bear = bear;
            }

            public abstract void act(Session session, Session.Shell shell) throws Exception;
        }

        public void withSession(WithLowLevelSession withSession) {
            try {
                final Session s = getSession();
//                final Session.Shell shell = s.startShell();
                withSession.act(s, null);

                if (!reuseSession) {
                    s.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        synchronized Session getSession() throws ConnectionException, TransportException {
            if (reuseSession) {
                if (session == null) {
                    session = newSession();
                }
                return session;
            } else {
                return newSession();
            }
        }

        private Session newSession() throws ConnectionException, TransportException {
            final Session s = getSsh().startSession();
            s.allocateDefaultPTY();
//            try {
//                s.getOutputStream().write("ls\n".getBytes(IOUtils.UTF8));
//                s.getOutputStream().flush();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
            return s;
        }
    }


    public static Address newUnixRemote(String name, String address) {
        return new SshAddress(name, null, null, address);
    }

    public static SshAddress newUnixRemote(String name, String username, String password, String address) {
        return new SshAddress(name, username, password, address);
    }

}
