/**
 * This file is part of mycollab-server-runner.
 *
 * mycollab-server-runner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mycollab-server-runner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with mycollab-server-runner.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.esofthead.mycollab.jetty;

import com.esofthead.mycollab.configuration.DatabaseConfiguration;
import com.esofthead.mycollab.configuration.SiteConfiguration;
import com.esofthead.mycollab.core.MyCollabException;
import com.esofthead.mycollab.core.utils.FileUtils;
import com.esofthead.mycollab.servlet.*;
import com.zaxxer.hikari.HikariDataSource;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.jndi.NamingContext;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.webapp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Generic MyCollab embedded server
 *
 * @author MyCollab Ltd.
 * @since 1.0
 */
public abstract class GenericServerRunner {
    private static final Logger LOG = LoggerFactory.getLogger(GenericServerRunner.class);

    private Server server;
    private int port = 8080;

    private InstallationServlet installServlet;
    private ContextHandlerCollection contexts;
    private WebAppContext appContext;
    private ServletContextHandler installationContextHandler;

    public abstract WebAppContext buildContext(String baseDir);

    /**
     * Detect web app folder
     *
     * @return
     */
    private String detectWebApp() {
        File webappFolder = FileUtils.getDesireFile(System.getProperty("user.dir"), "webapp", "src/main/webapp");

        if (webappFolder == null) {
            throw new MyCollabException("Can not detect webapp base folder");
        } else {
            return webappFolder.getAbsolutePath();
        }
    }

    /**
     * Run web server with arguments
     *
     * @param args
     * @throws Exception
     */
    void run(String[] args) throws Exception {
        ServerInstance.getInstance().registerInstance(this);
        System.setProperty("org.eclipse.jetty.annotations.maxWait", "180");
        int stopPort = 0;
        String stopKey = null;
        boolean isStop = false;

        for (int i = 0; i < args.length; i++) {
            if ("--stop-port".equals(args[i])) {
                stopPort = Integer.parseInt(args[++i]);
            } else if ("--stop-key".equals(args[i])) {
                stopKey = args[++i];
            } else if ("--stop".equals(args[i])) {
                isStop = true;
            } else if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[++i]);
            }
        }

        switch ((stopPort > 0 ? 1 : 0) + (stopKey != null ? 2 : 0)) {
            case 1:
                usage("Must specify --stop-key when --stop-port is specified");
                break;

            case 2:
                usage("Must specify --stop-port when --stop-key is specified");
                break;

            case 3:
                if (isStop) {
                    try (Socket s = new Socket(InetAddress.getByName("localhost"), stopPort);
                         OutputStream out = s.getOutputStream()) {
                        out.write((stopKey + "\r\nstop\r\n").getBytes());
                        out.flush();
                    }
                    return;
                } else {
                    ShutdownMonitor monitor = ShutdownMonitor.getInstance();
                    monitor.setPort(stopPort);
                    monitor.setKey(stopKey);
                    monitor.setExitVm(true);
                    break;
                }
        }

        execute();
    }

    private void execute() throws Exception {
        server = new Server((port > 0) ? port : 8080);
        contexts = new ContextHandlerCollection();

        if (!checkConfigFileExist()) {
            System.err
                    .println("It seems this is the first time you run MyCollab. For complete installation, you must open the brower and type address http://localhost:"
                            + port
                            + " and complete the steps to install MyCollab.");
            installationContextHandler = new ServletContextHandler(
                    ServletContextHandler.SESSIONS);
            installationContextHandler.setContextPath("/");

            installServlet = new InstallationServlet();
            installationContextHandler.addServlet(new ServletHolder(installServlet),
                    "/install");
            installationContextHandler.addServlet(new ServletHolder(
                    new DatabaseValidate()), "/validate");
            installationContextHandler.addServlet(new ServletHolder(
                    new EmailValidationServlet()), "/emailValidate");

            installationContextHandler.addServlet(new ServletHolder(
                    new AssetHttpServletRequestHandler()), "/assets/*");
            installationContextHandler.addServlet(new ServletHolder(
                    new SetupServlet()), "/*");
            installationContextHandler
                    .addLifeCycleListener(new ServerLifeCycleListener());

            server.setStopAtShutdown(true);
            contexts.setHandlers(new Handler[]{installationContextHandler});
        } else {
            WebAppContext appContext = initWebAppContext();
            ServletContextHandler upgradeContextHandler = new ServletContextHandler(
                    ServletContextHandler.SESSIONS);
            upgradeContextHandler.setServer(server);
            upgradeContextHandler.setContextPath("/it");
            upgradeContextHandler.addServlet(new ServletHolder(new UpgradeServlet()), "/upgrade");
            upgradeContextHandler.addServlet(new ServletHolder(new UpgradeStatusServlet()), "/upgrade_status");
            contexts.setHandlers(new Handler[]{upgradeContextHandler, appContext});
        }

        server.setHandler(contexts);
        server.start();

        ShutdownMonitor.getInstance().start();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                LOG.error("There is uncatch exception", e);
            }
        });

        server.join();
    }

    void upgrade(File upgradeFile) {
        try {
            appContext.stop();
        } catch (Exception e) {
            LOG.error("Error while starting server", e);
            throw new MyCollabException(e);
        }
        contexts.removeHandler(appContext);
        upgradeProcess(upgradeFile);
    }

    private void upgradeProcess(File upgradeFile) {
        try {
            unpackFile(upgradeFile);
        } catch (IOException e) {
            throw new MyCollabException("Exception when upgrade MyCollab", e);
        }

        appContext = initWebAppContext();
        appContext.setClassLoader(GenericServerRunner.class.getClassLoader());

        contexts.addHandler(appContext);
        try {
            appContext.start();
        } catch (Exception e) {
            LOG.error("Error while starting server", e);
            throw new MyCollabException(e);
        }
        ServerInstance.getInstance().setIsUpgrading(false);
    }

    private static void unpackFile(File upgradeFile) throws IOException {
        File libFolder = new File(System.getProperty("user.dir"), "lib");
        File webappFolder = new File(System.getProperty("user.dir"), "webapp");
        org.apache.commons.io.FileUtils.deleteDirectory(libFolder);
        org.apache.commons.io.FileUtils.deleteDirectory(webappFolder);

        byte[] buffer = new byte[2048];

        try (ZipInputStream inputStream = new ZipInputStream(new FileInputStream(upgradeFile))) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && (entry.getName().startsWith("lib/")
                        || entry.getName().startsWith("webapp"))) {
                    File candidateFile = new File(System.getProperty("user.dir"), entry.getName());
                    candidateFile.getParentFile().mkdirs();
                    try (FileOutputStream output = new FileOutputStream(candidateFile)) {
                        int len;
                        while ((len = inputStream.read(buffer)) > 0) {
                            output.write(buffer, 0, len);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new MyCollabException(e);
        }
    }

    private void usage(String error) {
        if (error != null)
            System.err.println("ERROR: " + error);
        System.err
                .println("Usage: java -jar runner.jar [--help|--version] [ server opts]");
        System.err.println("Server Options:");
        System.err
                .println(" --version                          - display version and exit");
        System.err.println(" --port n                      - server port");
        System.err
                .println(" --stop-port n                      - port to listen for stop command");
        System.err
                .println(" --stop-key n                       - security string for stop command (required if --stop-port is present)");
        System.exit(1);
    }

    private DataSource buildDataSource() {
        SiteConfiguration.loadInstance(port);

        DatabaseConfiguration dbConf = SiteConfiguration.getDatabaseConfiguration();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(dbConf.getDriverClass());
        dataSource.setJdbcUrl(dbConf.getDbUrl());
        dataSource.setUsername(dbConf.getUser());
        dataSource.setPassword(dbConf.getPassword());

        Properties dsProperties = new Properties();
        dsProperties.setProperty("cachePrepStmts", "true");
        dsProperties.setProperty("prepStmtCacheSize", "250");
        dsProperties.setProperty("prepStmtCacheSqlLimit", "2048");
        dsProperties.setProperty("useServerPrepStmts", "true");
        dataSource.setDataSourceProperties(dsProperties);
        return dataSource;
    }

    private boolean checkConfigFileExist() {
        File confFolder = FileUtils.getDesireFile(System.getProperty("user.dir"),
                "conf", "src/main/conf");
        return (confFolder == null) ? false : new File(confFolder, "mycollab.properties").exists();
    }

    private WebAppContext initWebAppContext() {
        String webAppDirLocation = detectWebApp();
        LOG.debug("Detect web location: {}", webAppDirLocation);
        appContext = buildContext(webAppDirLocation);
        appContext.setServer(server);
        appContext.setConfigurations(new Configuration[]{
                new AnnotationConfiguration(), new WebXmlConfiguration(),
                new WebInfConfiguration(), new PlusConfiguration(),
                new MetaInfConfiguration(), new FragmentConfiguration(),
                new EnvConfiguration()});

        String[] classPaths = System.getProperty("java.class.path").split(":");

        for (String classpath : classPaths) {
            if (classpath.matches("\\S+/mycollab-\\S+/target/classes$")) {
                LOG.info("Load classes in path " + classpath);
                appContext.getMetaData().addWebInfJar(new PathResource(new File(classpath)));
            } else if (classpath.matches("\\S+/mycollab-\\S+.jar$")) {
                try {
                    LOG.info("Load jar file in path " + classpath);
                    appContext.getMetaData().getWebInfClassesDirs().add(new FileResource(new File(classpath).toURI().toURL()));
                } catch (Exception e) {
                    LOG.error("Exception to resolve classpath: " + classpath, e);
                }
            }
        }

        File libFolder = new File(System.getProperty("user.dir"), "lib");
        if (libFolder.isDirectory()) {
            File[] files = libFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().matches("mycollab-\\S+.jar$")) {
                        LOG.info("Load jar file " + file.getName());
                        appContext.getMetaData().getWebInfClassesDirs().add(new FileResource(file.toURI()));
                    }
                }
            }
        }

        // Register a mock DataSource scoped to the webapp
        // This must be linked to the webapp via an entry in
        // web.xml:
        // <resource-ref>
        // <res-ref-name>jdbc/mydatasource</res-ref-name>
        // <res-type>javax.sql.DataSource</res-type>
        // <res-auth>Container</res-auth>
        // </resource-ref>
        // At runtime the webapp accesses this as
        // java:comp/env/jdbc/mydatasource
        try {
            NamingContext a;
            LOG.debug("Init the datasource");
            org.eclipse.jetty.plus.jndi.Resource mydatasource = new org.eclipse.jetty.plus.jndi.Resource(
                    appContext, "jdbc/mycollabdatasource", buildDataSource());
        } catch (NamingException e) {
            throw new MyCollabException(e);
        }

        return appContext;
    }

    private class ServerLifeCycleListener implements LifeCycle.Listener {

        @Override
        public void lifeCycleStarting(LifeCycle event) {

        }

        @Override
        public void lifeCycleStarted(LifeCycle event) {
            Runnable thread = new Runnable() {
                @Override
                public void run() {
                    LOG.debug("Detect root folder webapp");
                    File confFolder = FileUtils.getDesireFile(System.getProperty("user.dir"),
                            "conf", "src/main/conf");

                    if (confFolder == null) {
                        throw new MyCollabException("Can not detect webapp base folder");
                    } else {
                        File confFile = new File(confFolder, "mycollab.properties");
                        while (!confFile.exists()) {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                throw new MyCollabException(e);
                            }
                        }

                        appContext = initWebAppContext();
                        appContext.setClassLoader(GenericServerRunner.class.getClassLoader());

                        contexts.addHandler(appContext);
                        try {
                            appContext.start();
                        } catch (Exception e) {
                            LOG.error("Error while starting server", e);
                        }
                        installServlet.setWaitFlag(false);
                        contexts.removeHandler(installationContextHandler);
                    }
                }
            };

            new Thread(thread).start();
        }

        @Override
        public void lifeCycleFailure(LifeCycle event, Throwable cause) {

        }

        @Override
        public void lifeCycleStopping(LifeCycle event) {

        }

        @Override
        public void lifeCycleStopped(LifeCycle event) {

        }
    }
}
