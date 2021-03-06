package com.redhat.thermostat.server.core.web;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import com.redhat.thermostat.server.core.internal.web.configuration.ServerConfiguration;
import com.redhat.thermostat.server.core.internal.web.security.authentication.basic.BasicUserStore;
import com.redhat.thermostat.server.core.internal.web.security.authentication.basic.BasicAuthFilter;
import com.redhat.thermostat.server.core.internal.web.security.authentication.none.NoAuthFilter;
import com.redhat.thermostat.server.core.internal.web.security.authentication.proxy.ProxyAuthFilter;
import com.redhat.thermostat.server.core.internal.web.security.authorization.RoleAuthFilter;
import com.redhat.thermostat.server.core.internal.storage.mongo.ThermostatMongoStorage;
import com.redhat.thermostat.server.core.internal.web.http.BaseHttpHandler;
import com.redhat.thermostat.server.core.internal.web.http.NamespaceHttpHandler;
import com.redhat.thermostat.server.core.internal.storage.mongo.handler.MongoStorageHandler;
import com.redhat.thermostat.server.core.internal.web.swagger.SwaggerUiHandler;

@Component
@Service(CoreServer.class)
public class CoreServer {
    private Server server;
    private int port = 29000;

    public void buildServer(Map<String, String> serverConfig, Map<String, String> mongoConfig, Map<String, String> userConfig) {
        URI baseUri = UriBuilder.fromUri("http://localhost").port(8090).build();

        ResourceConfig resourceConfig = new ResourceConfig();
        setupResourceConfig(serverConfig, userConfig, resourceConfig);

        server = JettyHttpContainerFactory.createServer(baseUri, resourceConfig, false);

        setupConnectors(serverConfig);

        setupHandlers(serverConfig);

        ThermostatMongoStorage.start(mongoConfig);
    }

    private void setupResourceConfig(Map<String, String> serverConfig, Map<String, String> userConfig, ResourceConfig resourceConfig) {
        MongoStorageHandler storageHandler = new MongoStorageHandler();
        resourceConfig.register(new NamespaceHttpHandler(storageHandler));
        resourceConfig.register(new BaseHttpHandler(storageHandler));
        if (serverConfig.containsKey(ServerConfiguration.SECURITY_PROXY.toString()) &&
                serverConfig.get(ServerConfiguration.SECURITY_PROXY.toString()).equals("true")) {
            resourceConfig.register(new ProxyAuthFilter());
        } else if (serverConfig.containsKey(ServerConfiguration.SECURITY_BASIC.toString()) &&
                serverConfig.get(ServerConfiguration.SECURITY_BASIC.toString()).equals("true")) {
            resourceConfig.register(new BasicAuthFilter(new BasicUserStore(userConfig)));
        } else {
            resourceConfig.register(new NoAuthFilter());
        }

        resourceConfig.register(new RolesAllowedDynamicFeature());
        resourceConfig.register(new RoleAuthFilter());
    }

    private void setupConnectors(Map<String, String> serverConfig) {
        server.setConnectors(new Connector[]{});
        ServerConnector httpConnector = new ServerConnector(server);

        if (serverConfig.containsKey(ServerConfiguration.SECURITY_PROXY.toString()) &&
                serverConfig.get(ServerConfiguration.SECURITY_PROXY.toString()).equals("true")) {
            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConfig.addCustomizer(new org.eclipse.jetty.server.ForwardedRequestCustomizer());

            httpConnector.addConnectionFactory(new HttpConnectionFactory(httpConfig));
        } else if (serverConfig.containsKey(ServerConfiguration.SECURITY_BASIC.toString()) &&
                serverConfig.get(ServerConfiguration.SECURITY_BASIC.toString()).equals("true")) {
            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConnector.addConnectionFactory(new HttpConnectionFactory(httpConfig));
        } else {
            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConnector.addConnectionFactory(new HttpConnectionFactory(httpConfig));
        }

        try {
            URL url = new URL(serverConfig.get(ServerConfiguration.URL.toString()));
            httpConnector.setHost(url.getHost());
            port = url.getPort();
            httpConnector.setPort(port);
        } catch (MalformedURLException e) {
            httpConnector.setHost("localhost");
            httpConnector.setPort(port);
        }

        httpConnector.setIdleTimeout(30000);

        server.addConnector(httpConnector);
    }

    private void setupHandlers(Map<String, String> serverConfig) {
        if (serverConfig.containsKey(ServerConfiguration.SWAGGER_UI_ENABLED.toString()) &&
                serverConfig.get(ServerConfiguration.SWAGGER_UI_ENABLED.toString()).equals("true")) {
            ResourceHandler swaggerHandler = new SwaggerUiHandler().createSwaggerResourceHandler();
            if (swaggerHandler != null) {
                Handler originalHandler = server.getHandler();

                HandlerList handlers = new HandlerList();
                handlers.setHandlers(new Handler[]{swaggerHandler, originalHandler});

                server.setHandler(handlers);
            } else {
                System.err.println("Unable to add swagger UI resource handler. Resources invalid or not found.");
            }
        }
    }

    public Server getServer() {
        return server;
    }

    public void finish() {
        ThermostatMongoStorage.finish();
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getPort() {
        return port;
    }
}
