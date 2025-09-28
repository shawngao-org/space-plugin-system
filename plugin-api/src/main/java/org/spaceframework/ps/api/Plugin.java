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

package org.spaceframework.ps.api;

/**
 * Base interface that all plugins must implement to integrate with the Space Plugin System.
 *
 * <p>This interface defines the core contract for plugin lifecycle management and metadata
 * provision. All plugins must implement this interface to be recognized and managed by the plugin
 * system. The interface provides both lifecycle hooks for initialization and cleanup, as well as
 * metadata methods for plugin identification.
 *
 * <p>The plugin lifecycle consists of the following phases:
 *
 * <ol>
 *   <li><strong>Loading:</strong> The plugin JAR is loaded and the main class is instantiated
 *   <li><strong>Initialization:</strong> {@link #onLoad()} is called to initialize the plugin
 *   <li><strong>Active:</strong> The plugin is running and providing its functionality
 *   <li><strong>Shutdown:</strong> {@link #onUnload()} is called to clean up resources
 *   <li><strong>Unloading:</strong> The plugin is removed from the system
 * </ol>
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * @PluginInfo(
 *     id = "example-plugin",
 *     name = "Example Plugin",
 *     version = "1.0.0",
 *     description = "A sample plugin"
 * )
 * public class ExamplePlugin implements Plugin {
 *
 *     @Override
 *     public void onLoad() {
 *         // Initialize resources, register services, etc.
 *         System.out.println("Example plugin loaded!");
 *     }
 *
 *     @Override
 *     public void onUnload() {
 *         // Clean up resources, unregister services, etc.
 *         System.out.println("Example plugin unloaded!");
 *     }
 *
 *     @Override
 *     public String getPluginId() {
 *         return "example-plugin";
 *     }
 *
 *     // ... other required methods
 * }
 * }</pre>
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see PluginInfo
 */
public interface Plugin {

  /**
   * Called when the plugin is loaded and initialized by the plugin system.
   *
   * <p>This method is invoked after the plugin has been successfully loaded and its Spring context
   * has been created. Use this method to perform initialization tasks such as:
   *
   * <ul>
   *   <li>Setting up resources and connections
   *   <li>Registering services with the main application
   *   <li>Initializing plugin-specific configurations
   *   <li>Starting background tasks or schedulers
   * </ul>
   *
   * <p><strong>Note:</strong> This method should complete quickly and not perform long-running
   * operations that could block the plugin loading process. For long-running initialization tasks,
   * consider using asynchronous execution.
   *
   * @throws RuntimeException if initialization fails and the plugin should not be loaded
   */
  void onLoad();

  /**
   * Called when the plugin is being unloaded from the system.
   *
   * <p>This method is invoked before the plugin is removed from the system and its Spring context
   * is destroyed. Use this method to perform cleanup tasks such as:
   *
   * <ul>
   *   <li>Releasing resources and closing connections
   *   <li>Unregistering services from the main application
   *   <li>Stopping background tasks or schedulers
   *   <li>Persisting any necessary state information
   * </ul>
   *
   * <p><strong>Note:</strong> This method should complete quickly and handle any exceptions
   * gracefully to ensure proper plugin shutdown.
   */
  void onUnload();

  /**
   * Returns the unique identifier for this plugin.
   *
   * <p>This identifier must be unique across all plugins in the system and should match the ID
   * specified in the {@link PluginInfo} annotation. The plugin system uses this ID for tracking,
   * management operations, and dependency resolution.
   *
   * <p>The ID should follow a naming convention similar to Java package names (e.g.,
   * "com.example.my-plugin") to ensure uniqueness.
   *
   * @return the unique plugin identifier, never null or empty
   */
  String getPluginId();

  /**
   * Returns the human-readable name of the plugin.
   *
   * <p>This name is displayed to users in management interfaces, logs, and error messages. It
   * should be descriptive and user-friendly, clearly indicating what the plugin does.
   *
   * @return the plugin display name, never null or empty
   */
  String getPluginName();

  /**
   * Returns the version of the plugin.
   *
   * <p>The version should follow semantic versioning conventions (e.g., "1.0.0", "2.1.3-SNAPSHOT").
   * This information is used for compatibility checking, dependency resolution, and plugin
   * management operations.
   *
   * @return the plugin version string, never null or empty
   */
  String getVersion();

  /**
   * Returns a description of what this plugin does.
   *
   * <p>This description should provide a clear, concise explanation of the plugin's functionality
   * and purpose. It may be displayed in plugin management interfaces to help users understand what
   * the plugin provides.
   *
   * @return the plugin description, never null (may be empty)
   */
  String getDescription();

  /**
   * Returns the author(s) of the plugin.
   *
   * <p>This method provides a default implementation that returns "Unknown". Plugins can override
   * this method to provide information about the individual developer, organization, or team
   * responsible for creating and maintaining the plugin.
   *
   * @return the plugin author(s), defaults to "Unknown"
   */
  default String getAuthor() {
    return "Unknown";
  }
}
