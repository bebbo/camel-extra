/**************************************************************************************
 * Copyright (C) 2008 - 2012 Camel Extra Team. All rights reserved.                   *
 * http://code.google.com/a/apache-extras.org/p/camel-extra/                          *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package org.apachextras.camel.jboss;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.jboss.virtual.VFS;
import org.jboss.virtual.VirtualFile;
import org.jboss.virtual.VisitorAttributes;
import org.jboss.virtual.plugins.vfs.helpers.AbstractVirtualFileVisitor;

/**
 * JBoss specific package scan classloader to be used when Camel is running
 * inside JBoss Application Server.
 */
public class JBossPackageScanClassResolver extends DefaultPackageScanClassResolver {

    @Override
    protected void find(PackageScanFilter test, String packageName, ClassLoader loader, Set<Class<?>> classes) {
        if (log.isTraceEnabled()) {
            log.trace("Searching for: " + test + " in package: " + packageName
                    + " using classloader: " + loader.getClass().getName());
        }

        Enumeration<URL> urls;
        try {
            urls = getResources(loader, packageName);
            if (!urls.hasMoreElements()) {
                log.trace("No URLs returned by classloader");
            }
        }
        catch (IOException ioe) {
            log.warn("Could not read package: " + packageName, ioe);
            return;
        }

        while (urls.hasMoreElements()) {
            URL url = null;
            try {
                url = urls.nextElement();
                if (log.isTraceEnabled()) {
                    log.trace("URL from classloader: " + url);
                }
                VirtualFile root = VFS.getRoot(url);
                root.visit(new MatchingClassVisitor(test, classes));
            }
            catch (IOException ioe) {
                log.warn("Could not read entries in url: " + url, ioe);
            }
        }
    }

    private class MatchingClassVisitor extends AbstractVirtualFileVisitor {
        private static final String PREFIX_JAR = "jar/";
        private static final String PREFIX_CLASSES = "classes/";
        private PackageScanFilter filter;
        private Set<Class<?>> classes;

        private MatchingClassVisitor(PackageScanFilter filter, Set<Class<?>> classes) {
            super(VisitorAttributes.RECURSE_LEAVES_ONLY);
            this.filter = filter;
            this.classes = classes;
        }

        public void visit(VirtualFile file) {
            if (file.getName().endsWith(".class")) {
                String pathName = null;
                try {
                    pathName = file.toURI().toString();
                    // vfszip:/C:/prj/bin/jboss-5.0.0.GA/server/default/deploy/fpu.war/WEB-INF/lib/camel-ftp-2.4.0.jar/org/apache/camel/component/file/remote/FtpComponent.class
                } catch (MalformedURLException e) {
                    if (log.isWarnEnabled()) {
                        log.warn("Error while trying resolving uri for resource " + file.getName()
                        + "\nContinuing resource resolving throug simple name.", e);
                    }
                    pathName = file.getPathName();
                } catch (URISyntaxException e) {
                    if (log.isWarnEnabled()) {
                        log.warn("Error while trying resolving uri for resource " + file.getName()
                        + "\nContinuing resource resolving throug simple name.", e);
                    }
                    pathName = file.getPathName();
                }

                String fqn = pathName;
                String qn;
                if (fqn.indexOf(PREFIX_JAR) != -1) {
                    qn = fqn.substring(fqn.indexOf(PREFIX_JAR) + PREFIX_JAR.length());
                } else if (fqn.indexOf(PREFIX_CLASSES) != -1) {
                    qn = fqn.substring(fqn.indexOf(PREFIX_CLASSES) + PREFIX_CLASSES.length());
                } else {
                    qn = fqn.substring(fqn.indexOf("/") + 1);
                }
                addIfMatching(filter, qn, classes);
            }
        }
    }

}
