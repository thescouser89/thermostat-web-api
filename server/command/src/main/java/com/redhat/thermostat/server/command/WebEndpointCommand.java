/*
 * Copyright 2012-2017 Red Hat, Inc.
 *
 * This file is part of Thermostat.
 *
 * Thermostat is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2, or (at your
 * option) any later version.
 *
 * Thermostat is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Thermostat; see the file COPYING.  If not see
 * <http://www.gnu.org/licenses/>.
 *
 * Linking this code with other modules is making a combined work
 * based on this code.  Thus, the terms and conditions of the GNU
 * General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this code give
 * you permission to link this code with independent modules to
 * produce an executable, regardless of the license terms of these
 * independent modules, and to copy and distribute the resulting
 * executable under terms of your choice, provided that you also
 * meet, for each linked independent module, the terms and conditions
 * of the license of that module.  An independent module is a module
 * which is not derived from or based on this code.  If you modify
 * this code, you may extend this exception to your version of the
 * library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.redhat.thermostat.server.command;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.eclipse.jetty.server.Server;

import com.redhat.thermostat.common.cli.AbstractCommand;
import com.redhat.thermostat.common.cli.Command;
import com.redhat.thermostat.common.cli.CommandContext;
import com.redhat.thermostat.common.cli.CommandException;
import com.redhat.thermostat.common.config.experimental.ConfigurationInfoSource;
import com.redhat.thermostat.server.core.CoreServer;
import com.redhat.thermostat.shared.config.CommonPaths;

@Component
@Service(Command.class)
@Property(name=Command.NAME, value="web-server")
public class WebEndpointCommand extends AbstractCommand {

    @Reference
    private CoreServer coreServer;

    @Reference
    private CommonPaths paths;

    @Reference
    private ConfigurationInfoSource config;

    @Override
    public void run(CommandContext ctx) throws CommandException {
        try {
            Map<String, String> serverConfig = config.getConfiguration("web-server", "server-config.properties");
            Map<String, String> userConfig = config.getConfiguration("web-server", "user-config.properties");
            coreServer.buildServer(serverConfig, userConfig);
        } catch (IOException e) {
        }
        Server server = coreServer.getServer();
        try {
            server.start();
            System.out.println(server.dump());
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            coreServer.finish();
        }
    }

    @Override
    public boolean isStorageRequired() {
        return false;
    }
}