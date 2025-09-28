/*
 * Copyright (c) 2025 the original author or authors.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the “Software”), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.spaceframework.ps.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom ClassLoader for loading plugin classes in isolation from the main application.
 *
 * <p>This ClassLoader provides isolated class loading for plugins, ensuring that each plugin
 * operates in its own class loading environment. This prevents class conflicts between plugins and
 * allows for hot-swapping of plugin implementations without affecting the main application or other
 * plugins.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li><strong>Class Isolation:</strong> Each plugin gets its own ClassLoader instance
 *   <li><strong>Parent-First Loading:</strong> System classes are loaded by the parent ClassLoader
 *   <li><strong>Plugin-First Loading:</strong> Plugin-specific classes are loaded from the plugin
 *       JAR first
 *   <li><strong>Resource Management:</strong> Proper cleanup and resource management
 *   <li><strong>Caching:</strong> Loaded classes are cached for performance
 * </ul>
 *
 * <p>The ClassLoader follows a hybrid loading strategy:
 *
 * <ol>
 *   <li>System classes (java.*, springframework.*, etc.) are always loaded by the parent
 *   <li>Plugin classes are loaded from the plugin JAR first
 *   <li>If a class is not found in the plugin JAR, delegation to parent occurs
 * </ol>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * URL[] urls = {pluginJarFile.toURI().toURL()};
 * PluginClassLoader loader = new PluginClassLoader("my-plugin", pluginPath, urls, parentLoader);
 * Class<?> pluginClass = loader.loadClass("com.example.MyPlugin");
 * }</pre>
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see URLClassLoader
 * @see org.spaceframework.ps.manager.PluginManager
 */
public class PluginClassLoader extends URLClassLoader {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(PluginClassLoader.class);

  /** The unique identifier of the plugin this ClassLoader serves. */
  private final String pluginId;

  /** The file system path to the plugin JAR file. */
  private final Path pluginPath;

  /** Cache of loaded classes to improve performance and avoid reloading. */
  private final ConcurrentHashMap<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();

  /**
   * Constructs a new PluginClassLoader for the specified plugin.
   *
   * <p>This constructor creates an isolated ClassLoader for a plugin, allowing it to load classes
   * from its JAR file while maintaining proper delegation to the parent ClassLoader for system
   * classes.
   *
   * @param pluginId the unique identifier of the plugin, must not be null
   * @param pluginPath the file system path to the plugin JAR file, must not be null
   * @param urls the URLs from which to load classes and resources, must not be null
   * @param parent the parent ClassLoader for delegation, may be null
   */
  public PluginClassLoader(String pluginId, Path pluginPath, URL[] urls, ClassLoader parent) {
    super(urls, parent);
    this.pluginId = pluginId;
    this.pluginPath = pluginPath;
    logger.debug("Created PluginClassLoader for plugin: {} at path: {}", pluginId, pluginPath);
  }

  /**
   * Loads a class with the specified name using the plugin-first strategy.
   *
   * <p>This method implements a hybrid class loading strategy:
   *
   * <ol>
   *   <li>Check if the class is already loaded in the cache
   *   <li>If it's a system class, delegate to the parent ClassLoader
   *   <li>Try to load the class from the plugin JAR first
   *   <li>If not found in plugin JAR, delegate to the parent ClassLoader
   * </ol>
   *
   * <p>System classes (java.*, springframework.*, etc.) are always loaded by the parent to ensure
   * consistency and prevent security issues.
   *
   * @param name the fully qualified name of the class to load, must not be null
   * @param resolve if {@code true}, the class will be resolved after loading
   * @return the loaded Class object
   * @throws ClassNotFoundException if the class cannot be found by this ClassLoader or its parent
   * @see #isSystemClass(String)
   * @see #findClass(String)
   */
  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    // Check if class is already loaded
    Class<?> loadedClass = loadedClasses.get(name);
    if (loadedClass != null) {
      if (resolve) {
        resolveClass(loadedClass);
      }
      return loadedClass;
    }

    // Don't load system classes with this loader
    if (isSystemClass(name)) {
      return super.loadClass(name, resolve);
    }

    try {
      // Try to load the class from this plugin's JAR first
      loadedClass = findClass(name);
      loadedClasses.put(name, loadedClass);

      if (resolve) {
        resolveClass(loadedClass);
      }

      logger.debug("Loaded class {} for plugin {}", name, pluginId);
      return loadedClass;

    } catch (ClassNotFoundException e) {
      // If not found in plugin JAR, delegate to parent
      return super.loadClass(name, resolve);
    }
  }

  /**
   * Finds and loads a class from the plugin JAR file.
   *
   * <p>This method locates the class file within the plugin JAR, reads its bytecode, and defines
   * the class using the ClassLoader's defineClass method. The class bytes are read into memory and
   * then used to create the Class object.
   *
   * <p>The method converts the class name to a resource path by replacing dots with slashes and
   * appending the .class extension.
   *
   * @param name the fully qualified name of the class to find, must not be null
   * @return the Class object for the specified class name
   * @throws ClassNotFoundException if the class file cannot be found or read
   * @see #defineClass(String, byte[], int, int)
   */
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    try {
      String path = name.replace('.', '/') + ".class";
      URL resource = findResource(path);

      if (resource == null) {
        throw new ClassNotFoundException("Class not found: " + name);
      }

      try (InputStream is = resource.openStream();
          ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
          baos.write(buffer, 0, bytesRead);
        }

        byte[] classBytes = baos.toByteArray();
        return defineClass(name, classBytes, 0, classBytes.length);
      }

    } catch (IOException e) {
      throw new ClassNotFoundException("Failed to load class: " + name, e);
    }
  }

  /**
   * Finds a resource with the specified name, checking the plugin JAR first.
   *
   * <p>This method implements a plugin-first resource loading strategy. It first attempts to find
   * the resource in the plugin JAR file. If the resource is not found there, it delegates to the
   * parent ClassLoader.
   *
   * <p>This approach allows plugins to override resources from the parent ClassLoader while still
   * having access to system resources when needed.
   *
   * @param name the name of the resource to find, must not be null
   * @return a URL for reading the resource, or null if the resource could not be found
   * @see #findResource(String)
   */
  @Override
  public URL getResource(String name) {
    // Try to find resource in plugin JAR first
    URL resource = findResource(name);
    if (resource != null) {
      return resource;
    }

    // If not found, delegate to parent
    return super.getResource(name);
  }

  /**
   * Finds all resources with the specified name from both plugin JAR and parent ClassLoader.
   *
   * <p>This method combines resources from the plugin JAR and the parent ClassLoader, providing
   * access to all available resources with the given name. This is useful for cases where multiple
   * resources with the same name exist in different locations.
   *
   * <p>The returned enumeration first provides resources from the plugin JAR, followed by resources
   * from the parent ClassLoader.
   *
   * @param name the name of the resources to find, must not be null
   * @return an enumeration of URLs for the resources
   * @throws IOException if an I/O error occurs during resource lookup
   * @see CombinedEnumeration
   */
  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    // Combine resources from plugin JAR and parent classloader
    Enumeration<URL> pluginResources = findResources(name);
    Enumeration<URL> parentResources = super.getResources(name);

    return new CombinedEnumeration<>(pluginResources, parentResources);
  }

  /**
   * Determines if a class should be loaded by the system ClassLoader.
   *
   * <p>System classes include:
   *
   * <ul>
   *   <li>Java standard library classes (java.*)
   *   <li>Jakarta EE classes (jakarta.*)
   *   <li>Sun/Oracle internal classes (sun.*)
   *   <li>Spring Framework classes (org.springframework.*)
   *   <li>Logging framework classes (org.slf4j.*, ch.qos.logback.*)
   *   <li>Plugin API classes (com.icwind.pluginsystem.api.*)
   * </ul>
   *
   * <p>These classes are loaded by the parent ClassLoader to ensure consistency, security, and
   * proper framework integration.
   *
   * @param className the fully qualified name of the class to check, must not be null
   * @return {@code true} if the class should be loaded by the system ClassLoader, {@code false} if
   *     it should be loaded by this plugin ClassLoader
   */
  private boolean isSystemClass(String className) {
    return className.startsWith("java.")
        || className.startsWith("jakarta.")
        || className.startsWith("sun.")
        || className.startsWith("org.springframework.")
        || className.startsWith("org.slf4j.")
        || className.startsWith("ch.qos.logback.")
        || className.startsWith("com.icwind.pluginsystem.api.");
  }

  /**
   * Closes this ClassLoader and releases all associated resources.
   *
   * <p>This method performs cleanup operations including:
   *
   * <ul>
   *   <li>Clearing the loaded classes cache
   *   <li>Closing the underlying URLClassLoader resources
   *   <li>Releasing file handles to the plugin JAR
   * </ul>
   *
   * <p>After calling this method, the ClassLoader should not be used for loading additional classes
   * or resources.
   *
   * @throws IOException if an I/O error occurs during resource cleanup
   * @see URLClassLoader#close()
   */
  @Override
  public void close() throws IOException {
    logger.debug("Closing PluginClassLoader for plugin: {}", pluginId);
    loadedClasses.clear();
    super.close();
  }

  /**
   * Returns the unique identifier of the plugin this ClassLoader serves.
   *
   * @return the plugin ID, never null
   */
  public String getPluginId() {
    return pluginId;
  }

  /**
   * Returns the file system path to the plugin JAR file.
   *
   * @return the plugin JAR file path, never null
   */
  public Path getPluginPath() {
    return pluginPath;
  }

  /**
   * Utility class to combine two enumerations into a single enumeration.
   *
   * <p>This class provides a way to iterate over elements from two separate enumerations as if they
   * were a single enumeration. Elements from the first enumeration are returned before elements
   * from the second enumeration.
   *
   * <p>This is used internally by {@link #getResources(String)} to combine resources from the
   * plugin JAR and parent ClassLoader.
   *
   * @param <T> the type of elements in the enumerations
   * @param first The first enumeration to iterate over.
   * @param second The second enumeration to iterate over.
   * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
   * @version 1.0.0-SNAPSHOT
   * @since 1.0.0
   */
  private record CombinedEnumeration<T>(Enumeration<T> first, Enumeration<T> second)
      implements Enumeration<T> {

    /**
     * Constructs a new CombinedEnumeration with the specified enumerations.
     *
     * @param first the first enumeration to iterate over, must not be null
     * @param second the second enumeration to iterate over, must not be null
     */
    private CombinedEnumeration {}

    /**
     * Tests if this enumeration contains more elements.
     *
     * @return {@code true} if either enumeration has more elements, {@code false} otherwise
     */
    @Override
    public boolean hasMoreElements() {
      return first.hasMoreElements() || second.hasMoreElements();
    }

    /**
     * Returns the next element from the first enumeration if available, otherwise from the second
     * enumeration.
     *
     * @return the next element in the enumeration
     * @throws java.util.NoSuchElementException if no more elements exist
     */
    @Override
    public T nextElement() {
      if (first.hasMoreElements()) {
        return first.nextElement();
      }
      return second.nextElement();
    }
  }
}
