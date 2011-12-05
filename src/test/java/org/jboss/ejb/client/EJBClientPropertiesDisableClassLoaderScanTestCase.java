/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.ejb.client;

import java.util.Collection;

import org.jboss.ejb.client.test.client.EchoBean;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Note that this testcase *must* be run in a new JVM instance so that the {@link ConfigBasedEJBClientContextSelector}
 * is initialized with the correct set of properties that are set in the {@link #beforeClass()} of this testcase. We
 * use the forkMode=always of the Maven surefire plugin to ensure this behaviour (see the pom.xml of this project).
 * 
 * @author Jaikiran Pai
 */
public class EJBClientPropertiesDisableClassLoaderScanTestCase {

    private static final Logger logger = Logger.getLogger(EJBClientPropertiesDisableClassLoaderScanTestCase.class);

    private static DummyServer server;
    private static final String SERVER_ENDPOINT_NAME = "disable-cl-scan-test-case-endpoint";

    private static ClassLoader originalTCCL;

    @BeforeClass
    public static void beforeClass() throws Exception {
        // setup a resource switching classloader
        final ClassLoader resourceSwitchingCL = new ResourceSwitchingClassLoader("jboss-ejb-client.properties", "disable-cl-scan-jboss-ejb-client.properties");
        // switch TCCL
        originalTCCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(resourceSwitchingCL);

        // disable classloader scan for jboss-ejb-client.properties
        System.setProperty("jboss.ejb.client.properties.skip.classloader.scan", "true");
        logger.info("Disabled classpath scan of jboss-ejb-client.properties by setting jboss.ejb.client.properties.skip.classloader.scan system property");

        server = new DummyServer("localhost", 7999, SERVER_ENDPOINT_NAME);
        server.start();
        server.register("dummy-app", "dummy-module", "", EchoBean.class.getSimpleName(), new EchoBean());
    }


    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stop();
        }
        // switch back the CL
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null && tccl.getClass().getName().equals(ResourceSwitchingClassLoader.class.getName())) {
            Thread.currentThread().setContextClassLoader(originalTCCL);
        }
    }

    @Test
    public void testRemotingEJBReceiver() throws Exception {
        final ConfigBasedEJBClientContextSelector configBasedEJBClientContextSelector = ConfigBasedEJBClientContextSelector.INSTANCE;

        final EJBClientContext ejbClientContext = configBasedEJBClientContextSelector.getCurrent();
        logger.info("Found EJB client context " + ejbClientContext);
        Assert.assertNotNull("No client context found " + ejbClientContext);
        final Collection<EJBReceiver<?>> ejbReceivers = ejbClientContext.getEJBReceivers("dummy-app", "dummy-module", "");
        Assert.assertNotNull("No EJB receivers found ", ejbReceivers);
        // there should be no receivers because the classpath scan of jboss-ejb-client.properties was disabled
        // and also no explicit system property was set to point to a configuration file. So it should be an empty context
        Assert.assertEquals("Unexpected number of EJB receivers", 0, ejbReceivers.size());
    }

}