/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.web.servlet;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.coyote.AbstractProtocol;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.RequestLog;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.embedded.jetty.JettyWebServer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.servlet.server.Jsp;
import org.springframework.boot.web.servlet.server.Session;
import org.springframework.boot.web.servlet.server.Session.Cookie;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link DefaultServletWebServerFactoryCustomizer}.
 *
 * @author Brian Clozel
 * @author Yunkun Huang
 */
public class DefaultServletWebServerFactoryCustomizerTests {

	private final ServerProperties properties = new ServerProperties();

	private DefaultServletWebServerFactoryCustomizer customizer;

	@Captor
	private ArgumentCaptor<ServletContextInitializer[]> initializersCaptor;

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		this.customizer = new DefaultServletWebServerFactoryCustomizer(this.properties);
	}

	@Test
	public void tomcatAccessLogIsDisabledByDefault() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		assertThat(factory.getEngineValves()).isEmpty();
	}

	@Test
	public void tomcatAccessLogCanBeEnabled() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.accesslog.enabled", "true");
		bindProperties(map);
		this.customizer.customize(factory);
		assertThat(factory.getEngineValves()).hasSize(1);
		assertThat(factory.getEngineValves()).first().isInstanceOf(AccessLogValve.class);
	}

	@Test
	public void tomcatAccessLogFileDateFormatByDefault() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.accesslog.enabled", "true");
		bindProperties(map);
		this.customizer.customize(factory);
		assertThat(((AccessLogValve) factory.getEngineValves().iterator().next())
				.getFileDateFormat()).isEqualTo(".yyyy-MM-dd");
	}

	@Test
	public void tomcatAccessLogFileDateFormatCanBeRedefined() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.accesslog.enabled", "true");
		map.put("server.tomcat.accesslog.file-date-format", "yyyy-MM-dd.HH");
		bindProperties(map);
		this.customizer.customize(factory);
		assertThat(((AccessLogValve) factory.getEngineValves().iterator().next())
				.getFileDateFormat()).isEqualTo("yyyy-MM-dd.HH");
	}

	@Test
	public void tomcatAccessLogIsBufferedByDefault() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.accesslog.enabled", "true");
		bindProperties(map);
		this.customizer.customize(factory);
		assertThat(((AccessLogValve) factory.getEngineValves().iterator().next())
				.isBuffered()).isTrue();
	}

	@Test
	public void tomcatAccessLogBufferingCanBeDisabled() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.accesslog.enabled", "true");
		map.put("server.tomcat.accesslog.buffered", "false");
		bindProperties(map);
		this.customizer.customize(factory);
		assertThat(((AccessLogValve) factory.getEngineValves().iterator().next())
				.isBuffered()).isFalse();
	}

	@Test
	public void redirectContextRootCanBeConfigured() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.redirect-context-root", "false");
		bindProperties(map);
		ServerProperties.Tomcat tomcat = this.properties.getTomcat();
		assertThat(tomcat.getRedirectContextRoot()).isEqualTo(false);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		Context context = (Context) ((TomcatWebServer) factory.getWebServer()).getTomcat()
				.getHost().findChildren()[0];
		assertThat(context.getMapperContextRootRedirectEnabled()).isFalse();
	}

	@Test
	public void useRelativeRedirectsCanBeConfigured() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.use-relative-redirects", "true");
		bindProperties(map);
		ServerProperties.Tomcat tomcat = this.properties.getTomcat();
		assertThat(tomcat.getUseRelativeRedirects()).isTrue();
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		Context context = (Context) ((TomcatWebServer) factory.getWebServer()).getTomcat()
				.getHost().findChildren()[0];
		assertThat(context.getUseRelativeRedirects()).isTrue();
	}

	@Test
	public void errorReportValveIsConfiguredToNotReportStackTraces() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		Map<String, String> map = new HashMap<String, String>();
		bindProperties(map);
		this.customizer.customize(factory);
		Valve[] valves = ((TomcatWebServer) factory.getWebServer()).getTomcat().getHost()
				.getPipeline().getValves();
		assertThat(valves).hasAtLeastOneElementOfType(ErrorReportValve.class);
		for (Valve valve : valves) {
			if (valve instanceof ErrorReportValve) {
				ErrorReportValve errorReportValve = (ErrorReportValve) valve;
				assertThat(errorReportValve.isShowReport()).isFalse();
				assertThat(errorReportValve.isShowServerInfo()).isFalse();
			}
		}
	}

	@Test
	public void testCustomizeTomcat() {
		ConfigurableServletWebServerFactory factory = mock(
				ConfigurableServletWebServerFactory.class);
		this.customizer.customize(factory);
		verify(factory, never()).setContextPath("");
	}

	@Test
	public void testDefaultDisplayName() {
		ConfigurableServletWebServerFactory factory = mock(
				ConfigurableServletWebServerFactory.class);
		this.customizer.customize(factory);
		verify(factory).setDisplayName("application");
	}

	@Test
	public void testCustomizeDisplayName() {
		ConfigurableServletWebServerFactory factory = mock(
				ConfigurableServletWebServerFactory.class);
		this.properties.getServlet().setApplicationDisplayName("TestName");
		this.customizer.customize(factory);
		verify(factory).setDisplayName("TestName");
	}

	@Test
	public void testCustomizeSsl() {
		ConfigurableServletWebServerFactory factory = mock(
				ConfigurableServletWebServerFactory.class);
		Ssl ssl = mock(Ssl.class);
		this.properties.setSsl(ssl);
		this.customizer.customize(factory);
		verify(factory).setSsl(ssl);
	}

	@Test
	public void testCustomizeJsp() {
		ConfigurableServletWebServerFactory factory = mock(
				ConfigurableServletWebServerFactory.class);
		this.customizer.customize(factory);
		verify(factory).setJsp(any(Jsp.class));
	}

	@Test
	public void customizeSessionProperties() throws Exception {
		Map<String, String> map = new HashMap<>();
		map.put("server.servlet.session.timeout", "123");
		map.put("server.servlet.session.tracking-modes", "cookie,url");
		map.put("server.servlet.session.cookie.name", "testname");
		map.put("server.servlet.session.cookie.domain", "testdomain");
		map.put("server.servlet.session.cookie.path", "/testpath");
		map.put("server.servlet.session.cookie.comment", "testcomment");
		map.put("server.servlet.session.cookie.http-only", "true");
		map.put("server.servlet.session.cookie.secure", "true");
		map.put("server.servlet.session.cookie.max-age", "60");
		bindProperties(map);
		ConfigurableServletWebServerFactory factory = mock(
				ConfigurableServletWebServerFactory.class);
		this.customizer.customize(factory);
		ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
		verify(factory).setSession(sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().getTimeout())
				.isEqualTo(Duration.ofSeconds(123));
		Cookie cookie = sessionCaptor.getValue().getCookie();
		assertThat(cookie.getName()).isEqualTo("testname");
		assertThat(cookie.getDomain()).isEqualTo("testdomain");
		assertThat(cookie.getPath()).isEqualTo("/testpath");
		assertThat(cookie.getComment()).isEqualTo("testcomment");
		assertThat(cookie.getHttpOnly()).isTrue();
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(60));

	}

	@Test
	public void testCustomizeTomcatPort() {
		ConfigurableServletWebServerFactory factory = mock(
				ConfigurableServletWebServerFactory.class);
		this.properties.setPort(8080);
		this.customizer.customize(factory);
		verify(factory).setPort(8080);
	}

	@Test
	public void customizeTomcatDisplayName() {
		Map<String, String> map = new HashMap<>();
		map.put("server.servlet.application-display-name", "MyBootApp");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		assertThat(factory.getDisplayName()).isEqualTo("MyBootApp");
	}

	@Test
	public void disableTomcatRemoteIpValve() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.remote-ip-header", "");
		map.put("server.tomcat.protocol-header", "");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		assertThat(factory.getEngineValves()).isEmpty();
	}

	@Test
	public void defaultTomcatBackgroundProcessorDelay() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		TomcatWebServer webServer = (TomcatWebServer) factory.getWebServer();
		assertThat(webServer.getTomcat().getEngine().getBackgroundProcessorDelay())
				.isEqualTo(30);
		webServer.stop();
	}

	@Test
	public void customTomcatBackgroundProcessorDelay() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.background-processor-delay", "5");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		TomcatWebServer webServer = (TomcatWebServer) factory.getWebServer();
		assertThat(webServer.getTomcat().getEngine().getBackgroundProcessorDelay())
				.isEqualTo(5);
		webServer.stop();
	}

	@Test
	public void defaultTomcatRemoteIpValve() {
		Map<String, String> map = new HashMap<>();
		// Since 1.1.7 you need to specify at least the protocol
		map.put("server.tomcat.protocol-header", "X-Forwarded-Proto");
		map.put("server.tomcat.remote-ip-header", "X-Forwarded-For");
		bindProperties(map);
		testRemoteIpValveConfigured();
	}

	@Test
	public void setUseForwardHeadersTomcat() {
		// Since 1.3.0 no need to explicitly set header names if use-forward-header=true
		this.properties.setUseForwardHeaders(true);
		testRemoteIpValveConfigured();
	}

	@Test
	public void deduceUseForwardHeadersTomcat() {
		this.customizer.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		testRemoteIpValveConfigured();
	}

	private void testRemoteIpValveConfigured() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		assertThat(factory.getEngineValves()).hasSize(1);
		Valve valve = factory.getEngineValves().iterator().next();
		assertThat(valve).isInstanceOf(RemoteIpValve.class);
		RemoteIpValve remoteIpValve = (RemoteIpValve) valve;
		assertThat(remoteIpValve.getProtocolHeader()).isEqualTo("X-Forwarded-Proto");
		assertThat(remoteIpValve.getProtocolHeaderHttpsValue()).isEqualTo("https");
		assertThat(remoteIpValve.getRemoteIpHeader()).isEqualTo("X-Forwarded-For");
		String expectedInternalProxies = "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" // 10/8
				+ "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" // 192.168/16
				+ "169\\.254\\.\\d{1,3}\\.\\d{1,3}|" // 169.254/16
				+ "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" // 127/8
				+ "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" // 172.16/12
				+ "172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|"
				+ "172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}";
		assertThat(remoteIpValve.getInternalProxies()).isEqualTo(expectedInternalProxies);
	}

	@Test
	public void customTomcatRemoteIpValve() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.remote-ip-header", "x-my-remote-ip-header");
		map.put("server.tomcat.protocol-header", "x-my-protocol-header");
		map.put("server.tomcat.internal-proxies", "192.168.0.1");
		map.put("server.tomcat.port-header", "x-my-forward-port");
		map.put("server.tomcat.protocol-header-https-value", "On");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		assertThat(factory.getEngineValves()).hasSize(1);
		Valve valve = factory.getEngineValves().iterator().next();
		assertThat(valve).isInstanceOf(RemoteIpValve.class);
		RemoteIpValve remoteIpValve = (RemoteIpValve) valve;
		assertThat(remoteIpValve.getProtocolHeader()).isEqualTo("x-my-protocol-header");
		assertThat(remoteIpValve.getProtocolHeaderHttpsValue()).isEqualTo("On");
		assertThat(remoteIpValve.getRemoteIpHeader()).isEqualTo("x-my-remote-ip-header");
		assertThat(remoteIpValve.getPortHeader()).isEqualTo("x-my-forward-port");
		assertThat(remoteIpValve.getInternalProxies()).isEqualTo("192.168.0.1");
	}

	@Test
	public void customTomcatAcceptCount() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.accept-count", "10");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
		this.customizer.customize(factory);
		TomcatWebServer server = (TomcatWebServer) factory.getWebServer();
		server.start();
		try {
			assertThat(((AbstractProtocol<?>) server.getTomcat().getConnector()
					.getProtocolHandler()).getAcceptCount()).isEqualTo(10);
		}
		finally {
			server.stop();
		}
	}

	@Test
	public void customTomcatMaxConnections() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.max-connections", "5");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
		this.customizer.customize(factory);
		TomcatWebServer server = (TomcatWebServer) factory.getWebServer();
		server.start();
		try {
			assertThat(((AbstractProtocol<?>) server.getTomcat().getConnector()
					.getProtocolHandler()).getMaxConnections()).isEqualTo(5);
		}
		finally {
			server.stop();
		}
	}

	@Test
	public void customTomcatMaxHttpPostSize() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.max-http-post-size", "10000");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
		this.customizer.customize(factory);
		TomcatWebServer server = (TomcatWebServer) factory.getWebServer();
		server.start();
		try {
			assertThat(server.getTomcat().getConnector().getMaxPostSize())
					.isEqualTo(10000);
		}
		finally {
			server.stop();
		}
	}

	@Test
	public void customTomcatDisableMaxHttpPostSize() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.max-http-post-size", "-1");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
		this.customizer.customize(factory);
		TomcatWebServer server = (TomcatWebServer) factory.getWebServer();
		server.start();
		try {
			assertThat(server.getTomcat().getConnector().getMaxPostSize()).isEqualTo(-1);
		}
		finally {
			server.stop();
		}
	}

	@Test
	public void customizeUndertowAccessLog() {
		Map<String, String> map = new HashMap<>();
		map.put("server.undertow.accesslog.enabled", "true");
		map.put("server.undertow.accesslog.pattern", "foo");
		map.put("server.undertow.accesslog.prefix", "test_log");
		map.put("server.undertow.accesslog.suffix", "txt");
		map.put("server.undertow.accesslog.dir", "test-logs");
		map.put("server.undertow.accesslog.rotate", "false");
		bindProperties(map);
		UndertowServletWebServerFactory factory = spy(
				new UndertowServletWebServerFactory());
		this.customizer.customize(factory);
		verify(factory).setAccessLogEnabled(true);
		verify(factory).setAccessLogPattern("foo");
		verify(factory).setAccessLogPrefix("test_log");
		verify(factory).setAccessLogSuffix("txt");
		verify(factory).setAccessLogDirectory(new File("test-logs"));
		verify(factory).setAccessLogRotate(false);
	}

	@Test
	public void testCustomizeTomcatMinSpareThreads() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.min-spare-threads", "10");
		bindProperties(map);
		assertThat(this.properties.getTomcat().getMinSpareThreads()).isEqualTo(10);
	}

	@Test
	public void customTomcatTldSkip() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.additional-tld-skip-patterns", "foo.jar,bar.jar");
		bindProperties(map);
		testCustomTomcatTldSkip("foo.jar", "bar.jar");
	}

	@Test
	public void customTomcatTldSkipAsList() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.additional-tld-skip-patterns[0]", "biz.jar");
		map.put("server.tomcat.additional-tld-skip-patterns[1]", "bah.jar");
		bindProperties(map);
		testCustomTomcatTldSkip("biz.jar", "bah.jar");
	}

	private void testCustomTomcatTldSkip(String... expectedJars) {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		this.customizer.customize(factory);
		assertThat(factory.getTldSkipPatterns()).contains(expectedJars);
		assertThat(factory.getTldSkipPatterns()).contains("junit-*.jar",
				"spring-boot-*.jar");
	}

	@Test
	public void defaultUseForwardHeadersUndertow() {
		UndertowServletWebServerFactory factory = spy(
				new UndertowServletWebServerFactory());
		this.customizer.customize(factory);
		verify(factory).setUseForwardHeaders(false);
	}

	@Test
	public void setUseForwardHeadersUndertow() {
		this.properties.setUseForwardHeaders(true);
		UndertowServletWebServerFactory factory = spy(
				new UndertowServletWebServerFactory());
		this.customizer.customize(factory);
		verify(factory).setUseForwardHeaders(true);
	}

	@Test
	public void deduceUseForwardHeadersUndertow() {
		this.customizer.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		UndertowServletWebServerFactory factory = spy(
				new UndertowServletWebServerFactory());
		this.customizer.customize(factory);
		verify(factory).setUseForwardHeaders(true);
	}

	@Test
	public void defaultUseForwardHeadersJetty() {
		JettyServletWebServerFactory factory = spy(new JettyServletWebServerFactory());
		this.customizer.customize(factory);
		verify(factory).setUseForwardHeaders(false);
	}

	@Test
	public void setUseForwardHeadersJetty() {
		this.properties.setUseForwardHeaders(true);
		JettyServletWebServerFactory factory = spy(new JettyServletWebServerFactory());
		this.customizer.customize(factory);
		verify(factory).setUseForwardHeaders(true);
	}

	@Test
	public void deduceUseForwardHeadersJetty() {
		this.customizer.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		JettyServletWebServerFactory factory = spy(new JettyServletWebServerFactory());
		this.customizer.customize(factory);
		verify(factory).setUseForwardHeaders(true);
	}

	@Test
	public void sessionStoreDir() {
		Map<String, String> map = new HashMap<>();
		map.put("server.servlet.session.store-dir", "myfolder");
		bindProperties(map);
		JettyServletWebServerFactory factory = spy(new JettyServletWebServerFactory());
		this.customizer.customize(factory);
		ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
		verify(factory).setSession(sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().getStoreDir())
				.isEqualTo(new File("myfolder"));
	}

	@Test
	public void jettyAccessLogCanBeEnabled() {
		JettyServletWebServerFactory factory = new JettyServletWebServerFactory(0);
		Map<String, String> map = new HashMap<>();
		map.put("server.jetty.accesslog.enabled", "true");
		bindProperties(map);
		this.customizer.customize(factory);
		JettyWebServer webServer = (JettyWebServer) factory.getWebServer();
		try {
			NCSARequestLog requestLog = getNCSARequestLog(webServer);
			assertThat(requestLog.getFilename()).isNull();
			assertThat(requestLog.isAppend()).isFalse();
			assertThat(requestLog.isExtended()).isFalse();
			assertThat(requestLog.getLogCookies()).isFalse();
			assertThat(requestLog.getLogServer()).isFalse();
			assertThat(requestLog.getLogLatency()).isFalse();
		}
		finally {
			webServer.stop();
		}
	}

	@Test
	public void jettyAccessLogCanBeCustomized() throws IOException {
		File logFile = File.createTempFile("jetty_log", ".log");
		JettyServletWebServerFactory factory = new JettyServletWebServerFactory(0);
		Map<String, String> map = new HashMap<>();
		String timezone = TimeZone.getDefault().getID();
		map.put("server.jetty.accesslog.enabled", "true");
		map.put("server.jetty.accesslog.filename", logFile.getAbsolutePath());
		map.put("server.jetty.accesslog.file-date-format", "yyyy-MM-dd");
		map.put("server.jetty.accesslog.retention-period", "42");
		map.put("server.jetty.accesslog.append", "true");
		map.put("server.jetty.accesslog.extended-format", "true");
		map.put("server.jetty.accesslog.date-format", "HH:mm:ss");
		map.put("server.jetty.accesslog.locale", "en_BE");
		map.put("server.jetty.accesslog.time-zone", timezone);
		map.put("server.jetty.accesslog.log-cookies", "true");
		map.put("server.jetty.accesslog.log-server", "true");
		map.put("server.jetty.accesslog.log-latency", "true");
		bindProperties(map);
		this.customizer.customize(factory);
		JettyWebServer webServer = (JettyWebServer) factory.getWebServer();
		NCSARequestLog requestLog = getNCSARequestLog(webServer);
		try {
			assertThat(requestLog.getFilename()).isEqualTo(logFile.getAbsolutePath());
			assertThat(requestLog.getFilenameDateFormat()).isEqualTo("yyyy-MM-dd");
			assertThat(requestLog.getRetainDays()).isEqualTo(42);
			assertThat(requestLog.isAppend()).isTrue();
			assertThat(requestLog.isExtended()).isTrue();
			assertThat(requestLog.getLogDateFormat()).isEqualTo("HH:mm:ss");
			assertThat(requestLog.getLogLocale()).isEqualTo(new Locale("en", "BE"));
			assertThat(requestLog.getLogTimeZone()).isEqualTo(timezone);
			assertThat(requestLog.getLogCookies()).isTrue();
			assertThat(requestLog.getLogServer()).isTrue();
			assertThat(requestLog.getLogLatency()).isTrue();
		}
		finally {
			webServer.stop();
		}
	}

	private NCSARequestLog getNCSARequestLog(JettyWebServer webServer) {
		RequestLog requestLog = webServer.getServer().getRequestLog();
		assertThat(requestLog).isInstanceOf(NCSARequestLog.class);
		return (NCSARequestLog) requestLog;
	}

	@Test
	public void skipNullElementsForUndertow() {
		UndertowServletWebServerFactory factory = mock(
				UndertowServletWebServerFactory.class);
		this.customizer.customize(factory);
		verify(factory, never()).setAccessLogEnabled(anyBoolean());
	}

	@Test
	public void customTomcatStaticResourceCacheTtl() {
		Map<String, String> map = new HashMap<>();
		map.put("server.tomcat.resource.cache-ttl", "10000");
		bindProperties(map);
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
		this.customizer.customize(factory);
		TomcatWebServer server = (TomcatWebServer) factory.getWebServer();
		server.start();
		try {
			Tomcat tomcat = server.getTomcat();
			Context context = (Context) tomcat.getHost().findChildren()[0];
			assertThat(context.getResources().getCacheTtl()).isEqualTo(10000L);
		}
		finally {
			server.stop();
		}
	}

	private void bindProperties(Map<String, String> map) {
		ConfigurationPropertySource source = new MapConfigurationPropertySource(map);
		new Binder(source).bind("server", Bindable.ofInstance(this.properties));
	}

}
