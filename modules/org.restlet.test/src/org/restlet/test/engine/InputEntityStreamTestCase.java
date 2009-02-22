/**
 * Copyright 2005-2009 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of the following open
 * source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 (the "Licenses"). You can
 * select the license that you prefer but you may not use this file except in
 * compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.gnu.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.sun.com/cddl/cddl.html
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.test.engine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.restlet.engine.io.ByteUtils;
import org.restlet.engine.io.InputEntityStream;
import org.restlet.test.RestletTestCase;


/**
 * Test cases for the input entity stream.
 * 
 * @author <a href="mailto:kevin.a.conaway@gmail.com">Kevin Conaway</a>
 */
public class InputEntityStreamTestCase extends RestletTestCase {

    public void testRead() {
        final String data = "test data";
        final InputStream input = new ByteArrayInputStream(data.getBytes());
        assertEquals("test", ByteUtils
                .toString(new InputEntityStream(input, 4)));
    }
}
