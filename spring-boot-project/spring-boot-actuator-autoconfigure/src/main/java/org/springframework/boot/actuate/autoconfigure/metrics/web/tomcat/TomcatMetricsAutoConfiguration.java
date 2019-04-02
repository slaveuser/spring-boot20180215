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

package org.springframework.boot.actuate.autoconfigure.metrics.web.tomcat;

import java.util.Collections;

import io.micrometer.core.instrument.binder.tomcat.TomcatMetrics;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Manager;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link TomcatMetrics}.
 *
 * @author Andy Wilkinson
 * @since 2.0.0
 */
@ConditionalOnWebApplication
@ConditionalOnClass({ TomcatMetrics.class, Manager.class })
public class TomcatMetricsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(TomcatMetrics.class)
	public TomcatMetrics tomcatMetrics(ApplicationContext applicationContext) {
		Context context = findContext(applicationContext);
		return new TomcatMetrics(context == null ? null : context.getManager(),
				Collections.emptyList());
	}

	private Context findContext(ApplicationContext context) {
		if (!(context instanceof WebServerApplicationContext)) {
			return null;
		}
		WebServer webServer = ((WebServerApplicationContext) context).getWebServer();
		if (!(webServer instanceof TomcatWebServer)) {
			return null;
		}
		return findContext((TomcatWebServer) webServer);
	}

	private Context findContext(TomcatWebServer webServer) {
		for (Container child : webServer.getTomcat().getHost().findChildren()) {
			if (child instanceof Context) {
				return (Context) child;
			}
		}
		return null;
	}

}
