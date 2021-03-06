/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.rest.service;

import ch.qos.logback.access.jetty.RequestLogImpl;
import ch.qos.logback.access.servlet.TeeFilter;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServlet;
import org.apache.chemistry.opencmis.server.impl.CmisRepositoryContextListener;
import org.apache.chemistry.opencmis.server.impl.atompub.CmisAtomPubServlet;
import org.apache.chemistry.opencmis.server.impl.browser.CmisBrowserBindingServlet;
import org.apache.chemistry.opencmis.server.shared.BasicAuthCallContextHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.AsyncGzipFilter;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.service.Command;
import org.structr.api.service.RunnableService;
import org.structr.api.service.StructrServices;
import org.structr.common.PropertyView;
import org.structr.core.Services;
import org.structr.core.auth.SuperUserAuthenticator;
import org.structr.rest.DefaultResourceProvider;
import org.structr.rest.ResourceProvider;
import org.structr.rest.servlet.JsonRestServlet;
import org.tuckey.web.filters.urlrewrite.UrlRewriteFilter;

/**
 *
 *
 */
public class HttpService implements RunnableService {

	private static final Logger logger = LoggerFactory.getLogger(HttpService.class.getName());

	public static final String SERVLETS                      = "HttpService.servlets";
	public static final String RESOURCE_HANDLERS             = "HttpService.resourceHandlers";
	public static final String LIFECYCLE_LISTENERS           = "HttpService.lifecycle.listeners";
	public static final String MAIN_CLASS                    = "HttpService.mainClass";
	public static final String ASYNC                         = "HttpService.async";

	public static final String APPLICATION_TITLE             = "application.title";
	public static final String APPLICATION_HOST              = "application.host";
	public static final String APPLICATION_HTTP_PORT         = "application.http.port";
	public static final String APPLICATION_HTTPS_PORT        = "application.https.port";
	public static final String APPLICATION_HTTPS_ENABLED     = "application.https.enabled";
	public static final String APPLICATION_KEYSTORE_PATH     = "application.keystore.path";
	public static final String APPLICATION_KEYSTORE_PASSWORD = "application.keystore.password";

	// set of resource providers for this service
	private Set<ResourceProvider> resourceProviders = new LinkedHashSet<>();

	private enum LifecycleEvent {

		Started, Stopped
	}

	private Server server          = null;
	private String basePath        = null;
	private String applicationName = null;
	private String host            = null;
	private boolean async          = true;
	private int httpPort           = 8082;
	private int maxIdleTime        = 30000;
	private int requestHeaderSize  = 8192;
	private HashSessionManager hashSessionManager = null;

	private HttpConfiguration httpConfig;
	private HttpConfiguration httpsConfig;

	@Override
	public void startService() throws Exception {

		logger.info("Starting {} (host={}:{}, maxIdleTime={}, requestHeaderSize={})", new Object[]{applicationName, host, String.valueOf(httpPort), String.valueOf(maxIdleTime), String.valueOf(requestHeaderSize)});
		logger.info("Base path {}", basePath);
		logger.info("{} started at http://{}:{}", new Object[]{applicationName, String.valueOf(host), String.valueOf(httpPort)});

		server.start();


		// The jsp directory is created by the container, but we don't need it
		removeDir(basePath, "jsp");

		// send lifecycle event that the server has been started
		sendLifecycleEvent(LifecycleEvent.Started);
	}

	@Override
	public void stopService() {
	}

	@Override
	public boolean runOnStartup() {
		return true;
	}

	@Override
	public boolean isRunning() {
		return server != null && server.isRunning();
	}

	@Override
	public void injectArguments(Command command) {
	}

	@Override
	public void initialize(final StructrServices services, final Properties additionalConfig) throws ClassNotFoundException, IllegalAccessException, InstantiationException {

		final Properties finalConfig = new Properties();

		// Default configuration
		finalConfig.setProperty(APPLICATION_TITLE, "structr server");
		finalConfig.setProperty(APPLICATION_HOST, "0.0.0.0");
		finalConfig.setProperty(APPLICATION_HTTP_PORT, "8082");
		finalConfig.setProperty(APPLICATION_HTTPS_ENABLED, "false");
		finalConfig.setProperty(APPLICATION_HTTPS_PORT, "8083");
		finalConfig.setProperty(ASYNC, "true");
		finalConfig.setProperty(SERVLETS, "JsonRestServlet");

		finalConfig.setProperty("JsonRestServlet.class", JsonRestServlet.class.getName());
		finalConfig.setProperty("JsonRestServlet.path", "/structr/rest/*");
		finalConfig.setProperty("JsonRestServlet.resourceprovider", DefaultResourceProvider.class.getName());
		finalConfig.setProperty("JsonRestServlet.authenticator", SuperUserAuthenticator.class.getName());
		finalConfig.setProperty("JsonRestServlet.user.class", "org.structr.dynamic.User");
		finalConfig.setProperty("JsonRestServlet.user.autocreate", "false");
		finalConfig.setProperty("JsonRestServlet.defaultview", PropertyView.Public);
		finalConfig.setProperty("JsonRestServlet.outputdepth", "3");

		StructrServices.mergeConfiguration(finalConfig, additionalConfig);

		final String mainClassName = (String) finalConfig.get(MAIN_CLASS);

		Class mainClass = null;
		if (mainClassName != null) {

			logger.info("Running main class {}", new Object[]{mainClassName});

			try {
				mainClass = Class.forName(mainClassName);
			} catch (ClassNotFoundException ex) {
				logger.warn("Did not find class for main class from config " + mainClassName, ex);
			}

		}

		String sourceJarName = (mainClass != null ? mainClass : getClass()).getProtectionDomain().getCodeSource().getLocation().toString();
		final boolean isTest = Boolean.parseBoolean(finalConfig.getProperty(Services.TESTING, "false"));

		if (!isTest && StringUtils.stripEnd(sourceJarName, System.getProperty("file.separator")).endsWith("classes")) {

			String jarFile = System.getProperty("jarFile");
			if (StringUtils.isEmpty(jarFile)) {
				throw new IllegalArgumentException(getClass().getName() + " was started in an environment where the classloader cannot determine the JAR file containing the main class.\n"
					+ "Please specify the path to the JAR file in the parameter -DjarFile.\n"
					+ "Example: -DjarFile=${project.build.directory}/${project.artifactId}-${project.version}.jar");
			}
			sourceJarName = jarFile;
		}

		// load configuration from properties file
		applicationName   = finalConfig.getProperty(APPLICATION_TITLE);
		host              = finalConfig.getProperty(APPLICATION_HOST);
		basePath          = finalConfig.getProperty(Services.BASE_PATH);
		httpPort          = Services.parseInt(finalConfig.getProperty(APPLICATION_HTTP_PORT), 8082);
		maxIdleTime       = Services.parseInt(System.getProperty("maxIdleTime"), 30000);
		requestHeaderSize = Services.parseInt(System.getProperty("requestHeaderSize"), 8192);
		async             = Services.parseBoolean(finalConfig.getProperty(ASYNC), true);

		if (async) {
			logger.info("Running in asynchronous mode");
		}

		// other properties
		final String keyStorePath           = finalConfig.getProperty(APPLICATION_KEYSTORE_PATH);
		final String keyStorePassword       = finalConfig.getProperty(APPLICATION_KEYSTORE_PASSWORD);
		final String contextPath            = System.getProperty("contextPath", "/");
		final String logPrefix              = "structr";
		final boolean enableRewriteFilter   = true; // configurationFile.getProperty(Services.
		final boolean enableHttps           = Services.parseBoolean(finalConfig.getProperty(APPLICATION_HTTPS_ENABLED), false);
		final boolean enableGzipCompression = true; //
		final boolean logRequests           = false; //
		final int httpsPort                 = Services.parseInt(finalConfig.getProperty(APPLICATION_HTTPS_PORT), 8083);

		// get current base path
		basePath = System.getProperty("home", basePath);
		if (basePath.isEmpty()) {

			// use cwd and, if that fails, /tmp as a fallback
			basePath = System.getProperty("user.dir", "/tmp");
		}

		// create base directory if it does not exist
		final File baseDir = new File(basePath);
		if (!baseDir.exists()) {
			baseDir.mkdirs();
		}

		server = new Server(httpPort);
		final ContextHandlerCollection contexts = new ContextHandlerCollection();

		contexts.addHandler(new DefaultHandler());

		final ServletContextHandler servletContext = new ServletContextHandler(server, contextPath, true, true);
		final List<Connector> connectors = new LinkedList<>();

		// create resource collection from base path & source JAR
		try {
			servletContext.setBaseResource(new ResourceCollection(Resource.newResource(basePath), JarResource.newJarResource(Resource.newResource(sourceJarName))));

		} catch (Throwable t) {

			logger.warn("Base resource {} not usable: {}", new Object[]{basePath, t.getMessage()});
		}

		// this is needed for the filters to work on the root context "/"
		servletContext.addServlet("org.eclipse.jetty.servlet.DefaultServlet", "/");
		servletContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");

		try {

			// CMIS setup
			servletContext.addEventListener(new CmisRepositoryContextListener());

			final ServletHolder cmisAtomHolder = servletContext.addServlet(CmisAtomPubServlet.class.getName(), "/structr/cmis/atom/*");
			cmisAtomHolder.setInitParameter("callContextHandler", BasicAuthCallContextHandler.class.getName());
			cmisAtomHolder.setInitParameter("cmisVersion", "1.1");

			final ServletHolder cmisBrowserHolder = servletContext.addServlet(CmisBrowserBindingServlet.class.getName(), "/structr/cmis/browser/*");
			cmisBrowserHolder.setInitParameter("callContextHandler", BasicAuthCallContextHandler.class.getName());
			cmisBrowserHolder.setInitParameter("cmisVersion", "1.1");


		} catch (Throwable t) {
			logger.warn("", t);
		}

		hashSessionManager = new HashSessionManager();
		try {
			hashSessionManager.setStoreDirectory(new File(baseDir + "/sessions"));
		} catch (IOException ex) {
			logger.warn("Could not set custom session manager with session store directory {}/sessions", baseDir);
		}

		servletContext.getSessionHandler().setSessionManager(hashSessionManager);

		if (enableRewriteFilter) {

			final FilterHolder rewriteFilter = new FilterHolder(UrlRewriteFilter.class);
			rewriteFilter.setInitParameter("confPath", "urlrewrite.xml");
			servletContext.addFilter(rewriteFilter, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC));
		}

		if (enableGzipCompression) {

			final FilterHolder gzipFilter = async ? new FilterHolder(AsyncGzipFilter.class) : new FilterHolder(GzipFilter.class);
			gzipFilter.setInitParameter("mimeTypes", "text/html,text/plain,text/css,text/javascript,application/json");
			gzipFilter.setInitParameter("bufferSize", "32768");
			gzipFilter.setInitParameter("minGzipSize", "256");
			gzipFilter.setInitParameter("deflateCompressionLevel", "9");
			gzipFilter.setInitParameter("methods", "GET,POST,PUT,HEAD,DELETE");
			servletContext.addFilter(gzipFilter, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC));

		}

		contexts.addHandler(servletContext);

		// enable request logging
		if (logRequests || "true".equals(finalConfig.getProperty("log.requests", "false"))) {

			final String etcPath = basePath + "/etc";
			final File etcDir    = new File(etcPath);

			if (!etcDir.exists()) {

				etcDir.mkdir();
			}

			final String logbackConfFilePath = basePath + "/etc/logback-access.xml";
			final File logbackConfFile       = new File(logbackConfFilePath);

			if (!logbackConfFile.exists()) {

				// synthesize a logback accees log config file
				List<String> config = new LinkedList<>();

				config.add("<configuration>");
				config.add("  <appender name=\"FILE\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">");
				config.add("    <rollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\">");
				config.add("      <fileNamePattern>logs/" + logPrefix + "-%d{yyyy_MM_dd}.request.log.zip</fileNamePattern>");
				config.add("    </rollingPolicy>");
				config.add("    <encoder>");
				config.add("      <charset>UTF-8</charset>");
				config.add("      <pattern>%h %l %u %t \"%r\" %s %b %n%fullRequest%n%n%fullResponse</pattern>");
				config.add("    </encoder>");
				config.add("  </appender>");
				config.add("  <appender-ref ref=\"FILE\" />");
				config.add("</configuration>");

				try {
					logbackConfFile.createNewFile();
					FileUtils.writeLines(logbackConfFile, "UTF-8", config);

				} catch (IOException ioex) {

					logger.warn("Unable to write logback configuration.", ioex);
				}
			}

			final FilterHolder loggingFilter = new FilterHolder(TeeFilter.class);
			servletContext.addFilter(loggingFilter, "/*", EnumSet.of(DispatcherType.REQUEST, async ? DispatcherType.ASYNC : DispatcherType.FORWARD));
			loggingFilter.setInitParameter("includes", "");

			final RequestLogHandler requestLogHandler = new RequestLogHandler();
			final String logPath                      = basePath + "/logs";
			final File logDir                         = new File(logPath);

			// Create logs directory if not existing
			if (!logDir.exists()) {

				logDir.mkdir();

			}

			final RequestLogImpl requestLog = new RequestLogImpl();
			requestLog.setName("REQUESTLOG");
			requestLogHandler.setRequestLog(requestLog);

			final HandlerCollection handlers = new HandlerCollection();

			handlers.setHandlers(new Handler[]{contexts, requestLogHandler});

			server.setHandler(handlers);

		} else {

			server.setHandler(contexts);

		}

		final List<ContextHandler> resourceHandler = collectResourceHandlers(finalConfig);
		for (ContextHandler contextHandler : resourceHandler) {
			contexts.addHandler(contextHandler);
		}

		final Map<String, ServletHolder> servlets = collectServlets(finalConfig);

		// add servlet elements
		int position = 1;
		for (Map.Entry<String, ServletHolder> servlet : servlets.entrySet()) {

			final ServletHolder servletHolder = servlet.getValue();
			final String path = servlet.getKey();

			servletHolder.setInitOrder(position++);

			logger.info("Adding servlet {} for {}", new Object[]{servletHolder, path});

			servletContext.addServlet(servletHolder, path);
		}

		contexts.addHandler(servletContext);

		if (host != null && !host.isEmpty() && httpPort > -1) {

			httpConfig = new HttpConfiguration();
			httpConfig.setSecureScheme("https");
			httpConfig.setSecurePort(httpsPort);
			//httpConfig.setOutputBufferSize(8192);
			httpConfig.setRequestHeaderSize(requestHeaderSize);

			final ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));

			httpConnector.setHost(host);
			httpConnector.setPort(httpPort);

			connectors.add(httpConnector);

		} else {

			logger.warn("Unable to configure HTTP server port, please make sure that {} and {} are set correctly in structr.conf.", new Object[]{APPLICATION_HOST, APPLICATION_HTTP_PORT});
		}

		if (enableHttps) {

			if (httpsPort > -1 && keyStorePath != null && !keyStorePath.isEmpty() && keyStorePassword != null) {

				httpsConfig = new HttpConfiguration(httpConfig);
				httpsConfig.addCustomizer(new SecureRequestCustomizer());

				final SslContextFactory sslContextFactory = new SslContextFactory();
				sslContextFactory.setKeyStorePath(keyStorePath);
				sslContextFactory.setKeyStorePassword(keyStorePassword);

				final ServerConnector https = new ServerConnector(server,
					new SslConnectionFactory(sslContextFactory, "http/1.1"),
					new HttpConnectionFactory(httpsConfig));

				https.setPort(httpsPort);
				https.setIdleTimeout(500000);

				https.setHost(host);
				https.setPort(httpsPort);

				connectors.add(https);

			} else {

				logger.warn("Unable to configure SSL, please make sure that {}, {} and {} are set correctly in structr.conf.", new Object[]{
					APPLICATION_HTTPS_PORT,
					APPLICATION_KEYSTORE_PATH,
					APPLICATION_KEYSTORE_PASSWORD
				});
			}
		}

		if (!connectors.isEmpty()) {

			server.setConnectors(connectors.toArray(new Connector[0]));

		} else {

			logger.error("No connectors configured, aborting.");
			System.exit(0);
		}

		server.setStopTimeout(1000);
		server.setStopAtShutdown(true);
	}

	@Override
	public void initialized() {}

	@Override
	public void shutdown() {

		if (server != null) {

			try {
				server.stop();

			} catch (Exception ex) {

				logger.warn("Error while stopping Jetty server: {}", ex.getMessage());
			}
		}

		// send lifecycle event that the server has been stopped
		sendLifecycleEvent(LifecycleEvent.Stopped);
	}

	@Override
	public String getName() {
		return HttpService.class.getName();
	}

	@Override
	public boolean isVital() {
		return true;
	}

	public Set<ResourceProvider> getResourceProviders() {
		return resourceProviders;
	}

	public HashSessionManager getHashSessionManager() {
		return hashSessionManager;
	}

	// ----- private methods -----
	private List<ContextHandler> collectResourceHandlers(final Properties properties) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		final List<ContextHandler> resourceHandlers = new LinkedList<>();
		final String resourceHandlerList            = properties.getProperty(RESOURCE_HANDLERS, "");

		if (resourceHandlerList != null) {

			for (String resourceHandlerName : resourceHandlerList.split("[ \\t]+")) {

				final String contextPath = properties.getProperty(resourceHandlerName.concat(".contextPath"));
				if (contextPath != null) {

					final String resourceBase = properties.getProperty(resourceHandlerName.concat(".resourceBase"));
					if (resourceBase != null) {

						final String directoriesListed = properties.getProperty(resourceHandlerName.concat(".directoriesListed"));
						if (directoriesListed != null) {

							final String welcomeFiles = properties.getProperty(resourceHandlerName.concat(".welcomeFiles"));
							if (welcomeFiles != null) {

								ResourceHandler resourceHandler = new ResourceHandler();
								resourceHandler.setDirectoriesListed(Boolean.parseBoolean(directoriesListed));
								resourceHandler.setWelcomeFiles(StringUtils.split(welcomeFiles));
								resourceHandler.setResourceBase(resourceBase);
								resourceHandler.setCacheControl("max-age=0");
								resourceHandler.setEtags(true);

								ContextHandler staticResourceHandler = new ContextHandler();
								staticResourceHandler.setContextPath(contextPath);
								staticResourceHandler.setHandler(resourceHandler);

								resourceHandlers.add(staticResourceHandler);

							} else {

								logger.warn("Unable to register resource handler {}, missing {}.welcomeFiles", resourceHandlerName);

							}

						} else {

							logger.warn("Unable to register resource handler {}, missing {}.resourceBase", resourceHandlerName);

						}

					} else {

						logger.warn("Unable to register resource handler {}, missing {}.resourceBase", resourceHandlerName);
					}

				} else {

					logger.warn("Unable to register resource handler {}, missing {}.contextPath", resourceHandlerName);
				}
			}

		} else {

			logger.warn("No resource handlers configured for HttpService.");
		}

		return resourceHandlers;
	}

	private Map<String, ServletHolder> collectServlets(final Properties properties) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		final Map<String, ServletHolder> servlets = new LinkedHashMap<>();
		final String servletNameList              = properties.getProperty(SERVLETS, "");

		if (servletNameList != null) {

			for (String servletName : servletNameList.split("[ \\t]+")) {

				final String servletClassName = properties.getProperty(servletName.concat(".class"));
				if (servletClassName != null) {

					final String servletPath = properties.getProperty(servletName.concat(".path"));
					if (servletPath != null) {

						final HttpServlet servlet = (HttpServlet)Class.forName(servletClassName).newInstance();
						if (servlet instanceof HttpServiceServlet) {

							((HttpServiceServlet)servlet).getConfig().initializeFromProperties(properties, servletName, resourceProviders);
						}

						if (servletPath.endsWith("*")) {

							servlets.put(servletPath, new ServletHolder(servlet));

						} else {

							servlets.put(servletPath + "/*", new ServletHolder(servlet));
						}

					} else {

						logger.warn("Unable to register servlet {}, missing {}.path", servletName);
					}

				} else {

					logger.warn("Unable to register servlet {}, missing {}.class", servletName);
				}
			}

		} else {

			logger.warn("No servlets configured for HttpService.");
		}

		return servlets;
	}

	private void removeDir(final String basePath, final String directoryName) {

		final String strippedBasePath = StringUtils.stripEnd(basePath, "/");
		final File file               = new File(strippedBasePath + "/" + directoryName);

		if (file.isDirectory()) {

			try {

				FileUtils.deleteDirectory(file);

			} catch (IOException ex) {

				logger.error("Unable to delete directory {}: {}", new Object[]{directoryName, ex.getMessage()});
			}

		} else {

			file.delete();
		}
	}

	// ----- private methods -----
	private void sendLifecycleEvent(final LifecycleEvent event) {

		// instantiate and call lifecycle callbacks from configuration file
		final String listeners = Services.getInstance().getCurrentConfig().getProperty(LIFECYCLE_LISTENERS);
		if (listeners != null) {

			final String[] listenerClasses = listeners.split("[\\s ,;]+");
			for (String listenerClass : listenerClasses) {

				try {
					final HttpServiceLifecycleListener listener = (HttpServiceLifecycleListener) Class.forName(listenerClass).newInstance();
					switch (event) {

						case Started:
							listener.serverStarted();
							break;

						case Stopped:
							listener.serverStopped();
							break;
					}

				} catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {

					logger.error("Unable to send lifecycle event to listener " + listenerClass, ex);
				}
			}
		}
	}
}
