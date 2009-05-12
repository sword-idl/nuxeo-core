/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     bstefanescu
 */
package org.nuxeo.ecm.core.storage.sql.coremodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import junit.framework.TestCase;

import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.storage.sql.Binary;
import org.nuxeo.runtime.api.DefaultServiceProvider;
import org.nuxeo.runtime.services.streaming.StreamManager;
import org.nuxeo.runtime.services.streaming.StreamSource;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class TestRemotableBlob extends TestCase {

    @Override
    protected void setUp() throws Exception {
        // hack runtime to register a fake streaming service to avoid exceptions later
        DefaultServiceProvider sp = new DefaultServiceProvider();
            sp.registerService(StreamManager.class, new StreamManager() {
                public String addStream(StreamSource src) throws IOException {
                    return null;
                }
                public StreamSource getStream(String uri) throws IOException {
                    return null;
                }
                public boolean hasStream(String uri) {
                    return false;
                }
                public void removeStream(String uri) {
                }
                public void start() throws Exception {
                }
                public void stop() throws Exception {
                }
            });
        DefaultServiceProvider.setProvider(sp);
    }
    
    protected SQLBlob createSQLBlob() throws Exception {
        File file = File.createTempFile("nuxeo-test-", ".blob");
        file.deleteOnExit();
        Binary binary = new Binary(file, "abc");
        return new SQLBlob(binary, "test.txt", "text/plain", "UTF-8", "abc");        
    }
    
    public void testSerialization() throws Exception {
        SQLBlob blob = createSQLBlob();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(blob);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream in = new ObjectInputStream(bais);
        Object obj = in.readObject();
        
        assertTrue(obj instanceof StreamingBlob);
        StreamingBlob sblob = (StreamingBlob)obj;
        
        assertEquals(sblob.getFilename(), blob.getFilename());
        assertEquals(sblob.getDigest(), blob.getDigest());
        assertEquals(sblob.getEncoding(), blob.getEncoding());
        assertEquals(sblob.getLength(), blob.getLength());
        assertEquals(sblob.getMimeType(), blob.getMimeType());
    }
    
}
