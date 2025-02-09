/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.security;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.collection.ConcurrentHashMap;
import java.util.function.Function;

import sun.security.util.Debug;

/**
 * This class extends ClassLoader with additional support for defining
 * classes with an associated code source and permissions which are
 * retrieved by the system policy by default.
 *
 * @author  Li Gong
 * @author  Roland Schemers
 * @since 1.2
 */
public class SecureClassLoader extends ClassLoader {
    /*
     * If initialization succeed this is set to true and security checks will
     * succeed. Otherwise the object is not initialized and the object is
     * useless.
     */
    private final boolean initialized;

    /*
     * Map that maps the CodeSource to a ProtectionDomain. The key is a
     * CodeSourceKey class that uses a String instead of a URL to avoid
     * potential expensive name service lookups. This does mean that URLs that
     * are equivalent after nameservice lookup will be placed in separate
     * ProtectionDomains; however during policy enforcement these URLs will be
     * canonicalized and resolved resulting in a consistent set of granted
     * permissions.
     */
    private final Map<CodeSourceKey, ProtectionDomain> pdcache
            = new ConcurrentHashMap<>(11);

    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * Creates a new SecureClassLoader using the specified parent
     * class loader for delegation.
     *
     * <p>If there is a security manager, this method first
     * calls the security manager's {@code checkCreateClassLoader}
     * method  to ensure creation of a class loader is allowed.
     *
     * @param parent the parent ClassLoader
     * @exception  SecurityException  if a security manager exists and its
     *             {@code checkCreateClassLoader} method doesn't allow
     *             creation of a class loader.
     * @see SecurityManager#checkCreateClassLoader
     */
    protected SecureClassLoader(ClassLoader parent) {
        super(parent);
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        initialized = true;
    }

    /**
     * Creates a new SecureClassLoader using the default parent class
     * loader for delegation.
     *
     * <p>If there is a security manager, this method first
     * calls the security manager's {@code checkCreateClassLoader}
     * method  to ensure creation of a class loader is allowed.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             {@code checkCreateClassLoader} method doesn't allow
     *             creation of a class loader.
     * @see SecurityManager#checkCreateClassLoader
     */
    protected SecureClassLoader() {
        super();
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        initialized = true;
    }

    /**
     * Creates a new {@code SecureClassLoader} of the specified name and
     * using the specified parent class loader for delegation.
     *
     * @param name class loader name; or {@code null} if not named
     * @param parent the parent class loader
     *
     * @throws IllegalArgumentException if the given name is empty.
     *
     * @throws SecurityException  if a security manager exists and its
     *         {@link SecurityManager#checkCreateClassLoader()} method
     *         doesn't allow creation of a class loader.
     *
     * @since 9
     * @spec JPMS
     */
    protected SecureClassLoader(String name, ClassLoader parent) {
        super(name, parent);
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        initialized = true;
    }

    /**
     * Converts an array of bytes into an instance of class Class,
     * with an optional CodeSource. Before the
     * class can be used it must be resolved.
     * <p>
     * If a non-null CodeSource is supplied a ProtectionDomain is
     * constructed and associated with the class being defined.
     *
     * @param      name the expected name of the class, or {@code null}
     *                  if not known, using '.' and not '/' as the separator
     *                  and without a trailing ".class" suffix.
     * @param      b    the bytes that make up the class data. The bytes in
     *             positions {@code off} through {@code off+len-1}
     *             should have the format of a valid class file as defined by
     *             <cite>The Java&trade; Virtual Machine Specification</cite>.
     * @param      off  the start offset in {@code b} of the class data
     * @param      len  the length of the class data
     * @param      cs   the associated CodeSource, or {@code null} if none
     * @return the {@code Class} object created from the data,
     *         and optional CodeSource.
     * @exception  ClassFormatError if the data did not contain a valid class
     * @exception  IndexOutOfBoundsException if either {@code off} or
     *             {@code len} is negative, or if
     *             {@code off+len} is greater than {@code b.length}.
     *
     * @exception  SecurityException if an attempt is made to add this class
     *             to a package that contains classes that were signed by
     *             a different set of certificates than this class, or if
     *             the class name begins with "java.".
     */
    protected final Class<?> defineClass(String name,
                                         byte[] b, int off, int len,
                                         CodeSource cs)
    {
        return defineClass(name, b, off, len, getProtectionDomain(cs));
    }

    /**
     * Converts a {@link java.nio.ByteBuffer ByteBuffer}
     * into an instance of class {@code Class}, with an optional CodeSource.
     * Before the class can be used it must be resolved.
     * <p>
     * If a non-null CodeSource is supplied a ProtectionDomain is
     * constructed and associated with the class being defined.
     *
     * @param      name the expected name of the class, or {@code null}
     *                  if not known, using '.' and not '/' as the separator
     *                  and without a trailing ".class" suffix.
     * @param      b    the bytes that make up the class data.  The bytes from positions
     *                  {@code b.position()} through {@code b.position() + b.limit() -1}
     *                  should have the format of a valid class file as defined by
     *                  <cite>The Java&trade; Virtual Machine Specification</cite>.
     * @param      cs   the associated CodeSource, or {@code null} if none
     * @return the {@code Class} object created from the data,
     *         and optional CodeSource.
     * @exception  ClassFormatError if the data did not contain a valid class
     * @exception  SecurityException if an attempt is made to add this class
     *             to a package that contains classes that were signed by
     *             a different set of certificates than this class, or if
     *             the class name begins with "java.".
     *
     * @since  1.5
     */
    protected final Class<?> defineClass(String name, java.nio.ByteBuffer b,
                                         CodeSource cs)
    {
        return defineClass(name, b, getProtectionDomain(cs));
    }

    /**
     * Returns the permissions for the given CodeSource object.
     * <p>
     * This method is invoked by the defineClass method which takes
     * a CodeSource as an argument when it is constructing the
     * ProtectionDomain for the class being defined.
     *
     * @param codesource the codesource.
     *
     * @return the permissions granted to the codesource.
     *
     */
    protected PermissionCollection getPermissions(CodeSource codesource)
    {
        check();
        return new Permissions(); // ProtectionDomain defers the binding
    }

    /*
     * holder class for the static field "debug" to delay its initialization
     */
    private static class DebugHolder {
        private static final Debug debug = Debug.getInstance("scl");
    }

    /*
     * Returned cached ProtectionDomain for the specified CodeSource.
     */
    private ProtectionDomain getProtectionDomain(CodeSource cs) {
        if (cs == null) {
            return null;
        }

        // Use a CodeSourceKey object key. It should behave in the
        // same manner as the CodeSource when compared for equality except
        // that no nameservice lookup is done on the hostname (String comparison
        // only), and the fragment is not considered.
        CodeSourceKey key = new CodeSourceKey(cs);
        return pdcache.computeIfAbsent(key, new Function<>() {
            @Override
            public ProtectionDomain apply(CodeSourceKey key /* not used */) {
                PermissionCollection perms
                        = SecureClassLoader.this.getPermissions(cs);
                ProtectionDomain pd = new ProtectionDomain(
                        cs, perms, SecureClassLoader.this, null);
                if (DebugHolder.debug != null) {
                    DebugHolder.debug.println(" getPermissions " + pd);
                    DebugHolder.debug.println("");
                }
                return pd;
            }
        });
    }

    /*
     * Check to make sure the class loader has been initialized.
     */
    private void check() {
        if (!initialized) {
            throw new SecurityException("ClassLoader object not initialized");
        }
    }

    private static class CodeSourceKey {
        private final CodeSource cs;

        CodeSourceKey(CodeSource cs) {
            this.cs = cs;
        }

        @Override
        public int hashCode() {
            String locationNoFrag = cs.getLocationNoFragString();
            return locationNoFrag != null ? locationNoFrag.hashCode() : 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (!(obj instanceof CodeSourceKey)) {
                return false;
            }

            CodeSourceKey csk = (CodeSourceKey) obj;

            if (!Objects.equals(cs.getLocationNoFragString(),
                                csk.cs.getLocationNoFragString())) {
                return false;
            }

            return cs.matchCerts(csk.cs, true);
        }
    }
}
