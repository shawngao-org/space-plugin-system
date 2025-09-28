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

package org.spaceframework.ps.manager;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spaceframework.ps.api.Plugin;
import org.spaceframework.ps.api.PluginInfo;
import org.spaceframework.ps.loader.PluginClassLoader;
import org.spaceframework.ps.model.PluginDescriptor;
import org.spaceframework.ps.spring.PluginSpringContextManager;
import org.spaceframework.ps.watcher.PluginWatcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Core service responsible for managing the complete lifecycle of plugins within the system.
 *
 * <p>This service provides comprehensive plugin management capabilities including:
 *
 * <ul>
 *   <li>Loading plugins from JAR files with isolated class loading
 *   <li>Unloading plugins and cleaning up resources
 *   <li>Reloading plugins for hot-swapping functionality
 *   <li>Batch operations for loading/unloading all plugins
 *   <li>Plugin discovery and validation
 *   <li>Integration with Spring context management
 * </ul>
 *
 * <p>The PluginManager ensures thread-safe operations and maintains plugin isolation through custom
 * class loaders. It integrates with the Spring framework to provide dependency injection and
 * context management for plugins.
 *
 * <p>Configuration properties:
 *
 * <ul>
 *   <li><code>plugin.system.plugins-directory</code>: Directory to scan for plugin JAR files
 *       (default: ./plugins)
 *   <li><code>plugin.system.max-plugins</code>: Maximum number of plugins that can be loaded
 *       (default: 100)
 * </ul>
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see Plugin
 * @see PluginDescriptor
 * @see PluginSpringContextManager
 */
@Service
public class PluginManager {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);

  /**
   * Directory path where plugin JAR files are located. Configurable via {@code
   * plugin.system.plugins-directory} property.
   */
  @Value("${plugin.system.plugins-directory:./plugins}")
  private String pluginsDirectory;

  /**
   * Maximum number of plugins that can be loaded simultaneously. Configurable via {@code
   * plugin.system.max-plugins} property.
   */
  @Value("${plugin.system.max-plugins:100}")
  private int maxPlugins;

  /** Spring context manager for handling plugin-specific application contexts. */
  @Autowired private PluginSpringContextManager springContextManager;

  /** Thread-safe map storing all currently loaded plugin descriptors indexed by plugin ID. */
  private final Map<String, PluginDescriptor> loadedPlugins = new ConcurrentHashMap<>();

  /**
   * Loads a plugin from the specified JAR file path.
   *
   * <p>This method performs the complete plugin loading process:
   *
   * <ol>
   *   <li>Validates the JAR file and system constraints
   *   <li>Creates an isolated class loader for the plugin
   *   <li>Discovers and validates the plugin main class
   *   <li>Extracts plugin metadata from annotations
   *   <li>Creates and initializes the plugin instance
   *   <li>Sets up Spring context if applicable
   *   <li>Registers the plugin in the system
   * </ol>
   *
   * <p>The plugin JAR must contain a class that:
   *
   * <ul>
   *   <li>Implements the {@link Plugin} interface
   *   <li>Is annotated with {@link PluginInfo}
   *   <li>Has a public no-argument constructor
   * </ul>
   *
   * @param jarPath the path to the plugin JAR file, must exist and end with .jar
   * @return the plugin descriptor for the successfully loaded plugin
   * @throws Exception if plugin loading fails due to:
   *     <ul>
   *       <li>Maximum plugin limit reached
   *       <li>Invalid JAR file or path
   *       <li>No valid plugin class found
   *       <li>Missing or invalid {@link PluginInfo} annotation
   *       <li>Plugin with same ID already loaded
   *       <li>Plugin initialization failure
   *     </ul>
   *
   * @see PluginDescriptor
   * @see PluginInfo
   */
  public PluginDescriptor loadPlugin(Path jarPath) throws Exception {
    if (loadedPlugins.size() >= maxPlugins) {
      throw new IllegalStateException("Maximum number of plugins (" + maxPlugins + ") reached");
    }

    if (!Files.exists(jarPath) || !jarPath.toString().endsWith(".jar")) {
      throw new IllegalArgumentException("Invalid JAR file: " + jarPath);
    }

    logger.info("Loading plugin from: {}", jarPath);

    // Create plugin classloader
    URL jarUrl = jarPath.toUri().toURL();
    PluginClassLoader classLoader =
        new PluginClassLoader(
            jarPath.getFileName().toString(),
            jarPath,
            new URL[] {jarUrl},
            this.getClass().getClassLoader());

    try {
      // Find plugin main class
      Class<? extends Plugin> pluginClass = findPluginMainClass(jarPath, classLoader);

      if (pluginClass == null) {
        throw new IllegalArgumentException("No valid plugin class found in JAR: " + jarPath);
      }

      // Get plugin metadata
      PluginInfo pluginInfo = pluginClass.getAnnotation(PluginInfo.class);
      if (pluginInfo == null) {
        throw new IllegalArgumentException(
            "Plugin class must be annotated with @PluginInfo: " + pluginClass.getName());
      }

      // Check if plugin with same ID is already loaded
      if (loadedPlugins.containsKey(pluginInfo.id())) {
        throw new IllegalStateException(
            "Plugin with ID '" + pluginInfo.id() + "' is already loaded");
      }

      // Create plugin instance
      Plugin pluginInstance = pluginClass.getDeclaredConstructor().newInstance();

      // Create plugin descriptor
      PluginDescriptor descriptor =
          new PluginDescriptor(
              pluginInfo.id(),
              pluginInfo.name(),
              pluginInfo.version(),
              pluginInfo.description(),
              pluginInfo.author(),
              jarPath,
              pluginInstance,
              classLoader);

      // Initialize plugin
      try {
        pluginInstance.onLoad();
        if (pluginClass.isAnnotationPresent(PluginInfo.class)) {
          springContextManager.createPluginContext(descriptor);
        }
        loadedPlugins.put(pluginInfo.id(), descriptor);
        logger.info("Successfully loaded plugin: {} ({})", pluginInfo.name(), pluginInfo.id());
        return descriptor;

      } catch (Exception e) {
        descriptor.setState(PluginDescriptor.PluginState.ERROR);
        logger.error("Failed to initialize plugin: {}", pluginInfo.id(), e);
        throw new RuntimeException("Plugin initialization failed", e);
      }

    } catch (Exception e) {
      // Clean up classloader if plugin loading failed
      try {
        classLoader.close();
      } catch (IOException ioException) {
        logger.warn("Failed to close classloader for failed plugin: {}", jarPath, ioException);
      }
      throw e;
    }
  }

  /**
   * Unloads a plugin by its unique identifier.
   *
   * <p>This method performs a complete plugin unloading process:
   *
   * <ol>
   *   <li>Locates the plugin by ID
   *   <li>Destroys the plugin's Spring context
   *   <li>Calls the plugin's {@link Plugin#onUnload()} method
   *   <li>Closes the plugin's class loader
   *   <li>Removes the plugin from the loaded plugins registry
   *   <li>Updates the plugin state to UNLOADED
   * </ol>
   *
   * <p>If any step fails, the plugin state is set to ERROR and the failure is logged, but the
   * method continues to attempt cleanup of remaining resources.
   *
   * @param pluginId the unique identifier of the plugin to unload, must not be null
   * @return {@code true} if the plugin was successfully unloaded, {@code false} if the plugin was
   *     not found or unloading failed
   * @see Plugin#onUnload()
   * @see PluginDescriptor.PluginState#UNLOADED
   * @see PluginDescriptor.PluginState#ERROR
   */
  public boolean unloadPlugin(String pluginId) {
    PluginDescriptor descriptor = loadedPlugins.get(pluginId);
    if (descriptor == null) {
      logger.warn("Plugin not found for unloading: {}", pluginId);
      return false;
    }

    logger.info("Unloading plugin: {}", pluginId);

    try {
      springContextManager.destroyPluginContext(pluginId);
      // Call plugin's unload method
      descriptor.getPluginInstance().onUnload();

      // Close classloader
      if (descriptor.getClassLoader() instanceof PluginClassLoader) {
        ((PluginClassLoader) descriptor.getClassLoader()).close();
      }

      // Remove from loaded plugins
      loadedPlugins.remove(pluginId);
      descriptor.setState(PluginDescriptor.PluginState.UNLOADED);

      logger.info("Successfully unloaded plugin: {}", pluginId);
      return true;

    } catch (Exception e) {
      descriptor.setState(PluginDescriptor.PluginState.ERROR);
      logger.error("Failed to unload plugin: {}", pluginId, e);
      return false;
    }
  }

  /**
   * Reloads a plugin by unloading it and then loading it again from its JAR file.
   *
   * <p>This method provides hot-swapping functionality by performing a complete reload cycle. It
   * first unloads the existing plugin (including cleanup of resources and Spring context) and then
   * loads it again from the same JAR file location. This is useful for applying plugin updates
   * without restarting the entire application.
   *
   * <p>The reload process:
   *
   * <ol>
   *   <li>Retrieves the current plugin descriptor
   *   <li>Stores the JAR file path
   *   <li>Unloads the current plugin instance
   *   <li>Loads the plugin again from the same JAR file
   * </ol>
   *
   * <p><strong>Note:</strong> The JAR file should be updated before calling this method to ensure
   * the new version is loaded.
   *
   * @param pluginId the unique identifier of the plugin to reload, must not be null
   * @return the new plugin descriptor for the reloaded plugin
   * @throws Exception if the plugin is not found or if reloading fails
   * @see #unloadPlugin(String)
   * @see #loadPlugin(Path)
   */
  public PluginDescriptor reloadPlugin(String pluginId) throws Exception {
    PluginDescriptor descriptor = loadedPlugins.get(pluginId);
    if (descriptor == null) {
      throw new IllegalArgumentException("Plugin not found: " + pluginId);
    }

    Path jarPath = descriptor.getJarPath();
    logger.info("Reloading plugin: {} from {}", pluginId, jarPath);

    // Unload current plugin
    unloadPlugin(pluginId);

    // Load plugin again
    return loadPlugin(jarPath);
  }

  /**
   * Loads all plugin JAR files found in the configured plugins directory.
   *
   * <p>This method scans the plugins directory (configured via {@code
   * plugin.system.plugins-directory} property) for JAR files and attempts to load each one as a
   * plugin. If the plugins directory doesn't exist, it will be created automatically.
   *
   * <p>The loading process:
   *
   * <ol>
   *   <li>Ensures the plugins directory exists (creates if necessary)
   *   <li>Scans for all .jar files in the directory
   *   <li>Attempts to load each JAR file as a plugin
   *   <li>Logs any failures but continues processing remaining files
   * </ol>
   *
   * <p>Individual plugin loading failures do not stop the overall process. Failed plugins are
   * logged as errors, and the method continues with the next plugin.
   *
   * @see #loadPlugin(Path)
   */
  public void loadAllPlugins() throws IOException {
    Path pluginsDir = PluginWatcherService.getPluginsDir(pluginsDirectory, logger);

    try (Stream<Path> pathStream =
        Files.list(pluginsDir).filter(path -> path.toString().endsWith(".jar"))) {
      pathStream.forEach(
          jarPath -> {
            try {
              loadPlugin(jarPath);
            } catch (Exception e) {
              logger.error("Failed to load plugin from: {}", jarPath, e);
            }
          });
    } catch (IOException e) {
      logger.error("Failed to scan plugins directory: {}", pluginsDir, e);
    }
  }

  /**
   * Unloads all currently loaded plugins from the system.
   *
   * <p>This method iterates through all loaded plugins and unloads each one individually. It's
   * typically called during application shutdown to ensure proper cleanup of all plugin resources.
   *
   * <p>The unloading process:
   *
   * <ol>
   *   <li>Creates a snapshot of all loaded plugin IDs
   *   <li>Iterates through each plugin ID
   *   <li>Calls {@link #unloadPlugin(String)} for each plugin
   *   <li>Logs the completion of the process
   * </ol>
   *
   * <p>Individual plugin unloading failures are handled by the {@link #unloadPlugin(String)} method
   * and do not stop the overall process.
   *
   * @see #unloadPlugin(String)
   */
  public void unloadAllPlugins() {
    logger.info("Unloading all plugins...");

    List<String> pluginIds = new ArrayList<>(loadedPlugins.keySet());
    for (String pluginId : pluginIds) {
      unloadPlugin(pluginId);
    }

    logger.info("All plugins unloaded");
  }

  /**
   * Returns a defensive copy of all currently loaded plugin descriptors.
   *
   * <p>This method provides access to all loaded plugins without exposing the internal collection.
   * The returned collection is a snapshot at the time of the call and will not reflect subsequent
   * changes to the loaded plugins.
   *
   * @return a new collection containing all loaded plugin descriptors, never null (may be empty if
   *     no plugins are loaded)
   * @see PluginDescriptor
   */
  public Collection<PluginDescriptor> getLoadedPlugins() {
    return new ArrayList<>(loadedPlugins.values());
  }

  /**
   * Retrieves a plugin descriptor by its unique identifier.
   *
   * <p>This method provides direct access to a specific plugin's descriptor using its unique ID.
   * The descriptor contains all metadata and state information about the plugin.
   *
   * @param pluginId the unique identifier of the plugin, must not be null
   * @return the plugin descriptor if found, or {@code null} if no plugin with the specified ID is
   *     currently loaded
   * @see PluginDescriptor
   */
  public PluginDescriptor getPlugin(String pluginId) {
    return loadedPlugins.get(pluginId);
  }

  /**
   * Checks whether a plugin with the specified ID is currently loaded.
   *
   * <p>This is a convenience method to quickly determine if a plugin is available without
   * retrieving the full descriptor.
   *
   * @param pluginId the unique identifier of the plugin to check, must not be null
   * @return {@code true} if a plugin with the specified ID is loaded, {@code false} otherwise
   */
  public boolean isPluginLoaded(String pluginId) {
    return loadedPlugins.containsKey(pluginId);
  }

  /**
   * Returns the total number of currently loaded plugins.
   *
   * <p>This method provides a quick way to get the current plugin count without retrieving all
   * plugin descriptors.
   *
   * @return the number of loaded plugins, always non-negative
   */
  public int getLoadedPluginCount() {
    return loadedPlugins.size();
  }

  /**
   * Discovers and returns the main plugin class from a JAR file.
   *
   * <p>This private method scans through all classes in the specified JAR file to find a class
   * that:
   *
   * <ul>
   *   <li>Implements the {@link Plugin} interface
   *   <li>Is annotated with {@link PluginInfo}
   *   <li>Is not an inner class (doesn't contain '$' in the name)
   * </ul>
   *
   * <p>The method uses the provided class loader to load and inspect each class. Classes that
   * cannot be loaded (due to missing dependencies, etc.) are skipped with debug logging.
   *
   * @param jarPath the path to the JAR file to scan, must not be null
   * @param classLoader the class loader to use for loading classes, must not be null
   * @return the main plugin class if found, or {@code null} if no valid plugin class exists
   * @throws Exception if there's an error reading the JAR file
   * @see Plugin
   * @see PluginInfo
   */
  @SuppressWarnings("unchecked")
  private Class<? extends Plugin> findPluginMainClass(Path jarPath, PluginClassLoader classLoader)
      throws Exception {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> entries = jarFile.entries();

      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();

        if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
          String className =
              entry.getName().replace('/', '.').substring(0, entry.getName().length() - 6);

          try {
            Class<?> clazz = classLoader.loadClass(className);

            if (Plugin.class.isAssignableFrom(clazz)
                && clazz.isAnnotationPresent(PluginInfo.class)) {
              return (Class<? extends Plugin>) clazz;
            }
          } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Skip classes that can't be loaded
            logger.debug("Skipping class that couldn't be loaded: {}", className);
          }
        }
      }
    }

    return null;
  }
}
