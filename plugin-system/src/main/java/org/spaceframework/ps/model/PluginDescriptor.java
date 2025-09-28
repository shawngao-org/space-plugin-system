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

package org.spaceframework.ps.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import org.spaceframework.ps.api.Plugin;

/**
 * Descriptor class that holds comprehensive metadata and state information about a loaded plugin.
 *
 * <p>This class serves as a container for all information related to a plugin instance within the
 * plugin system. It maintains both static metadata (such as plugin ID, name, version) and dynamic
 * state information (such as current state and load time). The descriptor is created when a plugin
 * is successfully loaded and is used throughout the plugin's lifecycle for management and
 * monitoring purposes.
 *
 * <p>The descriptor includes:
 *
 * <ul>
 *   <li>Plugin identification and metadata
 *   <li>Runtime state and lifecycle information
 *   <li>References to the plugin instance and its class loader
 *   <li>File system location of the plugin JAR
 * </ul>
 *
 * <p>This class is immutable except for the plugin state, which can be updated as the plugin
 * transitions through different lifecycle phases.
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see Plugin
 * @see PluginState
 */
public class PluginDescriptor {

  /** The unique identifier of the plugin. */
  private final String pluginId;

  /** The human-readable name of the plugin. */
  private final String name;

  /** The version string of the plugin. */
  private final String version;

  /** The description of the plugin's functionality. */
  private final String description;

  /** The author(s) of the plugin. */
  private final String author;

  /** The file system path to the plugin's JAR file. */
  private final Path jarPath;

  /** The actual plugin instance. */
  private final Plugin pluginInstance;

  /** The class loader used to load the plugin. */
  private final ClassLoader classLoader;

  /** The timestamp when the plugin was loaded. */
  private final LocalDateTime loadedAt;

  /** The current state of the plugin (mutable). */
  private PluginState state;

  /**
   * Enumeration representing the possible states of a plugin.
   *
   * <p>A plugin can be in one of the following states:
   *
   * <ul>
   *   <li><strong>LOADED:</strong> Plugin is successfully loaded and active
   *   <li><strong>UNLOADED:</strong> Plugin has been unloaded from the system
   *   <li><strong>ERROR:</strong> Plugin encountered an error and is not functional
   * </ul>
   */
  public enum PluginState {
    /** Plugin is successfully loaded and active. */
    LOADED,

    /** Plugin has been unloaded from the system. */
    UNLOADED,

    /** Plugin encountered an error and is not functional. */
    ERROR
  }

  /**
   * Constructs a new PluginDescriptor with the specified metadata and references.
   *
   * <p>This constructor creates a descriptor for a newly loaded plugin. The plugin state is
   * automatically set to {@link PluginState#LOADED} and the load time is set to the current
   * timestamp.
   *
   * @param pluginId the unique identifier of the plugin, must not be null or empty
   * @param name the human-readable name of the plugin, must not be null or empty
   * @param version the version string of the plugin, must not be null or empty
   * @param description the description of the plugin's functionality, may be empty but not null
   * @param author the author(s) of the plugin, may be empty but not null
   * @param jarPath the file system path to the plugin's JAR file, must not be null
   * @param pluginInstance the actual plugin instance, must not be null
   * @param classLoader the class loader used to load the plugin, must not be null
   * @throws IllegalArgumentException if any required parameter is null or empty
   */
  public PluginDescriptor(
      String pluginId,
      String name,
      String version,
      String description,
      String author,
      Path jarPath,
      Plugin pluginInstance,
      ClassLoader classLoader) {
    this.pluginId = pluginId;
    this.name = name;
    this.version = version;
    this.description = description;
    this.author = author;
    this.jarPath = jarPath;
    this.pluginInstance = pluginInstance;
    this.classLoader = classLoader;
    this.loadedAt = LocalDateTime.now();
    this.state = PluginState.LOADED;
  }

  /**
   * Returns the unique identifier of the plugin.
   *
   * @return the plugin ID, never null or empty
   */
  public String getPluginId() {
    return pluginId;
  }

  /**
   * Returns the human-readable name of the plugin.
   *
   * @return the plugin name, never null or empty
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the version string of the plugin.
   *
   * @return the plugin version, never null or empty
   */
  public String getVersion() {
    return version;
  }

  /**
   * Returns the description of the plugin's functionality.
   *
   * @return the plugin description, never null (may be empty)
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the author(s) of the plugin.
   *
   * @return the plugin author(s), never null (may be empty)
   */
  public String getAuthor() {
    return author;
  }

  /**
   * Returns the file system path to the plugin's JAR file.
   *
   * @return the JAR file path, never null
   */
  public Path getJarPath() {
    return jarPath;
  }

  /**
   * Returns the actual plugin instance.
   *
   * <p>This provides access to the loaded plugin object that implements the {@link Plugin}
   * interface. Use this to invoke plugin methods or access plugin-specific functionality.
   *
   * @return the plugin instance, never null
   */
  public Plugin getPluginInstance() {
    return pluginInstance;
  }

  /**
   * Returns the class loader used to load the plugin.
   *
   * <p>This class loader provides isolated loading of plugin classes and resources. It can be used
   * to load additional classes or resources from the plugin's JAR file.
   *
   * @return the plugin's class loader, never null
   */
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  /**
   * Returns the timestamp when the plugin was loaded.
   *
   * @return the load timestamp, never null
   */
  public LocalDateTime getLoadedAt() {
    return loadedAt;
  }

  /**
   * Returns the current state of the plugin.
   *
   * @return the plugin state, never null
   * @see PluginState
   */
  public PluginState getState() {
    return state;
  }

  /**
   * Updates the current state of the plugin.
   *
   * <p>This method is used by the plugin system to track the plugin's lifecycle state. State
   * transitions should follow the plugin lifecycle rules (e.g., LOADED → UNLOADED, LOADED → ERROR).
   *
   * @param state the new plugin state, must not be null
   * @throws IllegalArgumentException if state is null
   */
  public void setState(PluginState state) {
    this.state = state;
  }

  /**
   * Returns a string representation of this plugin descriptor.
   *
   * <p>The string includes the plugin ID, name, version, and current state for easy identification
   * and debugging purposes.
   *
   * @return a string representation of the descriptor
   */
  @Override
  public String toString() {
    return String.format(
        "PluginDescriptor{id='%s', name='%s', version='%s', state=%s}",
        pluginId, name, version, state);
  }
}
