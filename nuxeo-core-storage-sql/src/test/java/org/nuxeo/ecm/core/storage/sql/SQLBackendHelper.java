/*
 * (C) Copyright 2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core.storage.sql;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.FileUtils;

/**
 * Helper to set up and tear down a test database.
 * <p>
 * This can be used also to use another test database than Derby, for instance
 * PostgreSQL.
 *
 * @author Florent Guillaume
 */
public class SQLBackendHelper {

    private static final Log log = LogFactory.getLog(SQLBackendHelper.class);

    protected static enum Database {
        DERBY, //
        POSTGRESQL;
    };

    /**
     * Change this to use another SQL database for tests.
     */
    public static final Database DATABASE = Database.DERBY;

    protected static String REPOSITORY_NAME = "test";

    /*
     * ----- Derby configuration -----
     */

    /** This directory will be deleted and recreated. */
    protected static final String DERBY_DIRECTORY = "target/test/derby";

    protected static final String DERBY_LOG = "target/test/derby.log";

    /*
     * ----- PostgreSQL configuration -----
     */

    protected static final String PG_HOST = "localhost";

    protected static final String PG_PORT = "5432";

    /** Superuser that can create and drop databases. */
    protected static final String PG_SUPER_USER = "postgres";

    /** Superusers's password. */
    protected static final String PG_SUPER_PASSWORD = "";

    /** Database to connect to to issue CREATE DATABASE commands. */
    protected static final String PG_SUPER_DATABASE = "postgres";

    /* Constants mentioned in the ...pg-contrib.xml file: */

    /** The name of the database where tests take place. */
    public static final String PG_DATABASE = "nuxeojunittests";

    /** The owner of the database where tests take place. */
    public static final String PG_DATABASE_OWNER = "nuxeo";

    /** The password of the {@link #PG_DATABASE_OWNER} user. */
    public static final String PG_DATABASE_PASSWORD = "nuxeo";

    /*
     * ----- API -----
     */

    /**
     * Deploy the repository, returns an array of deployment contribs to do,
     * with two elements per contrib, first is the bundle, and second is the
     * filename.
     */
    public static void setUpRepository() throws Exception {
        switch (DATABASE) {
        case DERBY:
            setUpRepositoryDerby();
            return;
        case POSTGRESQL:
            setUpRepositoryPostgreSQL();
            return;
        }
        throw new RuntimeException(); // not reached
    }

    public static void tearDownRepository() throws Exception {
        switch (DATABASE) {
        case DERBY:
            tearDownRepositoryDerby();
            return;
        case POSTGRESQL:
            tearDownRepositoryPostgreSQL();
            return;
        }
        throw new RuntimeException(); // not reached
    }

    /*
     * ----- Derby -----
     */

    protected static void setUpRepositoryDerby() {
        File dbdir = new File(DERBY_DIRECTORY);
        File parent = dbdir.getParentFile();
        FileUtils.deleteTree(dbdir);
        parent.mkdirs();
        System.setProperty("derby.stream.error.file",
                new File(DERBY_LOG).getAbsolutePath());
    }

    protected static void tearDownRepositoryDerby() throws Exception {
        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException e) {
            if ("Derby system shutdown.".equals(e.getMessage())) {
                return;
            }
        }
        throw new RuntimeException("Expected Derby shutdown exception");
    }

    /*
     * ----- PostgreSQL -----
     */

    protected static void setUpRepositoryPostgreSQL() throws Exception {
        Class.forName("org.postgresql.Driver");
        String url = String.format("jdbc:postgresql://%s:%s/%s", PG_HOST,
                PG_PORT, PG_SUPER_DATABASE);
        Connection connection = DriverManager.getConnection(url, PG_SUPER_USER,
                PG_SUPER_PASSWORD);
        Statement st = connection.createStatement();
        String sql;
        sql = String.format("DROP DATABASE IF EXISTS \"%s\"", PG_DATABASE);
        log.debug(sql);
        st.execute(sql);
        sql = String.format("CREATE DATABASE \"%s\" OWNER \"%s\"", PG_DATABASE,
                PG_DATABASE_OWNER);
        log.debug(sql);
        st.execute(sql);
        st.close();
        connection.close();
    }

    protected static void tearDownRepositoryPostgreSQL() {
    }

}
