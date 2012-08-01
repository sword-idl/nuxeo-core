/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core.storage.sql.ra;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.repository.RepositoryDescriptor;
import org.nuxeo.ecm.core.repository.RepositoryFactory;
import org.nuxeo.ecm.core.storage.sql.coremodel.SQLRepository;
import org.nuxeo.runtime.jtajca.NuxeoContainer;

/**
 * Pooling repository factory.
 * <p>
 * This class is mentioned in the repository extension point defining a given
 * repository.
 * <p>
 * To function properly, it needs the bundle nuxeo-runtime-jtajca to be depoyed.
 */
public class PoolingRepositoryFactory implements RepositoryFactory {

    private static final Log log = LogFactory.getLog(PoolingRepositoryFactory.class);

    @Override
    public Repository createRepository(RepositoryDescriptor descriptor)
            throws Exception {
        String repositoryName = descriptor.getName();
        log.info("Creating pooling repository: " + repositoryName);
        ManagedConnectionFactory managedConnectionFactory = new ManagedConnectionFactoryImpl(
                SQLRepository.getDescriptor(descriptor));
        ConnectionManager connectionManager = lookupConnectionManager(descriptor);
        return (Repository) managedConnectionFactory.createConnectionFactory(connectionManager);
    }

    /**
     * Various binding names for the ConnectionManager. They depend on the
     * application server used and how the configuration is done.
     */
    private static final String[] CM_NAMES_PREFIXES = {
            "java:comp/NuxeoConnectionManager/",
            "java:comp/env/NuxeoConnectionManager/",
            "java:NuxeoConnectionManager/" };

    protected static ConnectionManager lookupConnectionManager(
            RepositoryDescriptor descriptor) throws NamingException {
        String repositoryName = descriptor.getName();
        // Check in container
        ConnectionManager cm = NuxeoContainer.getConnectionManager(repositoryName);
        if (cm != null) {
            return cm;
        }
        // Check in JNDI
        InitialContext context = new InitialContext();
        for (String name : CM_NAMES_PREFIXES) {
            try {
                cm = (ConnectionManager) context.lookup(name + repositoryName);
                if (cm != null) {
                    return cm;
                }
            } catch (NamingException e) {
                // try next one
            }
        }
        // Creation from descriptor pool config
        cm = NuxeoContainer.installConnectionManager(repositoryName,
                descriptor.getPool());
        if (cm != null) {
            return cm;
        }
        throw new NamingException("NuxeoConnectionManager not found in JNDI");
    }
}
