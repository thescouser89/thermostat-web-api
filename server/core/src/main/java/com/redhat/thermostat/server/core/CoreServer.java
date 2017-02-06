package com.redhat.thermostat.server.core;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.eclipse.jetty.util.resource.Resource;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import com.redhat.thermostat.server.core.internal.security.UserStore;
import com.redhat.thermostat.server.core.internal.security.auth.proxy.ProxyAuthFilter;
import com.redhat.thermostat.server.core.internal.storage.ThermostatMongoStorage;
import com.redhat.thermostat.server.core.internal.web.handler.http.CoreHttpHandler;
import com.redhat.thermostat.server.core.internal.web.handler.storage.MongoCoreStorageHandler;

@Component
@Service(CoreServer.class)
public class CoreServer {
    private Server server;

    public void buildServer(Map<String, String> serverConfig, Map<String, String> userConfig) {
        ThermostatMongoStorage.start(27518);

        URI baseUri = UriBuilder.fromUri("http://localhost").port(8080).build();

        ResourceConfig resourceConfig = new ResourceConfig();
        setupResourceConfig(serverConfig, userConfig, resourceConfig);

        server = JettyHttpContainerFactory.createServer(baseUri, resourceConfig, false);

        setupConnectors(serverConfig);

        setupHandlers(serverConfig);
    }

    private void setupResourceConfig(Map<String, String> serverConfig, Map<String, String> userConfig, ResourceConfig resourceConfig) {
        resourceConfig.register(new CoreHttpHandler(new MongoCoreStorageHandler()));
        resourceConfig.register(new ProxyAuthFilter(new UserStore(userConfig)));
        resourceConfig.register(new RolesAllowedDynamicFeature());
    }

    private void setupConnectors(Map<String, String> serverConfig) {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.addCustomizer(new org.eclipse.jetty.server.ForwardedRequestCustomizer());

        ServerConnector httpConnector = new ServerConnector(server);
        httpConnector.addConnectionFactory(new HttpConnectionFactory(httpConfig));

        httpConnector.setHost("localhost");
        httpConnector.setPort(8090);
        httpConnector.setIdleTimeout(30000);

        server.setConnectors(new Connector[]{httpConnector});
    }

    private void setupHandlers(Map<String, String> serverConfig) {
        Handler originalHandler = server.getHandler();

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { createSwaggerResource(), originalHandler});

        server.setHandler(handlers);
    }

    private Handler createSwaggerResource() {
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        resourceHandler.setWelcomeFiles(new String[]{ "index.html" });
        resourceHandler.setResourceBase("");
        URL u = this.getClass().getResource("/swagger/index.html");
        URI root;
        try {
            root = u.toURI().resolve("./").normalize();
            resourceHandler.setBaseResource(Resource.newResource(root));
        } catch (URISyntaxException | MalformedURLException e) {
            e.printStackTrace();
        }
        return resourceHandler;
    }

    public Server getServer() {
        return server;
    }

    public void finish() {
        ThermostatMongoStorage.finish();
    }
}