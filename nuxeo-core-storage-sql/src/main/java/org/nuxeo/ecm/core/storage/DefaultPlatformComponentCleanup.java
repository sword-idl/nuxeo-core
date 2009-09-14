/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */
package org.nuxeo.ecm.core.storage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.NXCore;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * This class should be used in conjunction with an <implementation> tag inside
 * the platform-config.xml file for your deployment.
 * 
 * @author Ian Smith<ismith@nuxeo.com>
 * 
 */
public class DefaultPlatformComponentCleanup extends DefaultComponent {
    private static final String REPOSITORY_NAME_IN_DEFAULT_PLATFORM = "default";

    private static final Log log = LogFactory.getLog(DefaultPlatformComponentCleanup.class);

    @Override
    public void activate(ComponentContext context) throws Exception {
        log.warn("activating cleanup activator component!");
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        try {
            Repository repo = NXCore.getRepository(REPOSITORY_NAME_IN_DEFAULT_PLATFORM);
            log.warn("Cleaning up default repository from default-platform.xml");
            if (repo instanceof DefaultPlatformComponentCleanupConnectionFactory) {
                log.info("cleanup default-platfrom.xml: found a connection factory");
                DefaultPlatformComponentCleanupConnectionFactory cf = (DefaultPlatformComponentCleanupConnectionFactory) repo;
                DefaultPlatformComponentCleanupManagedConnectionFactory mcf = cf.getManagedConnectionFactory();
                log.info("cleanup default-platform.xml: got managed connection factory...");
                mcf.terminateRepository();
                log.info("cleanup default-platform.xml: terminated repository successfully");
            } else {
                log.warn("Unable to cleanup default-platform.xml because "
                        + "we found unexpected repo type:"
                        + repo.getClass().getName());
            }
        } catch (Exception e) {
            log.error("Was trying to cleanup default-platform.xml and failed!",
                    e);
        }
    }
}

