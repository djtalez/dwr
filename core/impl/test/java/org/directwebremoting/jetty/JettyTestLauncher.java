/*
 * Copyright 2005 Joe Walker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.directwebremoting.jetty;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.log.Log;
import org.mortbay.util.Scanner;

/**
 * Launch Jetty embedded.
 * It's also worth looking at the Eclipse Jetty Runner as an alternative
 * http://code.google.com/p/run-jetty-run/wiki/GettingStarted
 * @author Joe Walker [joe at getahead dot org]
 */
public class JettyTestLauncher
{
    /**
     * Path (can be relative) to the web application (aka context)
     * This directory should contain a WEB-INF/web.xml file
     */
    public static final String CONTEXT_HOME = "target/ant/web/test";

    /**
     * URL component to which we deploy the application, which goes something
     * like this: http://example.com/CONTEXT_PATH/path_to_something_in_the_webapp
     */
    public static final String CONTEXT_PATH = "/dwr-test";

    /**
     * The port to listen on
     */
    public static final int PORT = 8080;

    /**
     * Just create and launch an instance of Jetty
     * @param args program args. Ignored.
     * @throws Exception This is such a small program we ignore exceptions
     */
    public static void main(String[] args) throws Exception
    {
        JettyTestLauncher launcher = new JettyTestLauncher(CONTEXT_HOME, CONTEXT_PATH, PORT);
        launcher.start();
    }

    /**
     * Sets up the server.
     * @param contextHome See comments for {@link #CONTEXT_HOME}
     * @param contextPath See comments for {@link #CONTEXT_PATH}
     */
    @SuppressWarnings("unchecked")
    public JettyTestLauncher(String contextHome, final String contextPath, int port)
    {
        server = new Server();

        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        server.addConnector(connector);
        server.setStopAtShutdown(true);

        final File contextRoot = new File(contextHome);

        File webXml = new File(contextRoot, "WEB-INF/web.xml");
        if (!webXml.isFile())
        {
            Log.warn("Warning: Failed to find web application. Missing web.xml: " + webXml.getAbsolutePath());
            Log.warn("- Alter " + this.getClass().getName() + ".CONTEXT_HOME, or hack the source to remove this warning.");
        }

        List<File> scanList = new ArrayList<File>();

        scanList.add(webXml);
        findFiles("", new File(contextRoot, "WEB-INF"), scanList);

        Log.info("Scanning files: " + scanList);

        scanner = new Scanner();
        scanner.setScanInterval(5);
        scanner.setScanDirs(scanList);
        scanner.setReportExistingFilesOnStartup(false);

        Log.info("#### Initializing context: " + contextPath);
        final WebAppContext context = new WebAppContext(contextRoot.getAbsolutePath(), contextPath);

        server.addHandler(context);

        // This should prevent Jetty from using memory mapped buffers (which
        // causes file locking on windows). If we discover that it doesn't then
        // we need to investigate this more:
        // http://docs.codehaus.org/display/JETTY/Files+locked+on+Windows
        context.getInitParams().put("useFileMappedBuffer", "false");

        scanner.addListener(new Scanner.BulkListener()
        {
            public void filesChanged(List changedFiles)
            {
                try
                {
                    Log.info("#### Stopping context: " + contextPath + " from " + contextRoot.getAbsolutePath());
                    context.stop();

                    Log.info("#### Starting context: " + contextPath + " from " + contextRoot.getAbsolutePath());
                    context.start();
                }
                catch (Exception ex)
                {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    /**
     * Start the web serving/file scanning threads
     */
    public void start() throws Exception
    {
        scanner.start();
        server.start();
        server.join();
    }

    /**
     * Find all the files with a given <code>extension</code> recursively under
     * <code>baseDir</code> and place them into <code>found</code>.
     * Warning: If you're doing anything funky with directory loops, then life
     * ends here. There is no depth protection.
     */
    private void findFiles(final String extension, File baseDir, final Collection<File> found)
    {
        baseDir.listFiles(new FileFilter()
        {
            public boolean accept(File pathname)
            {
                if (pathname.isDirectory())
                {
                    findFiles(extension, pathname, found);
                }
                else if (pathname.getName().endsWith(extension))
                {
                    found.add(pathname);
                }
                return false;
            }
        });
    }

    /**
     * The Jetty web server
     */
    private final Server server;

    /**
     * The file change scanner
     */
    private final Scanner scanner;
}
