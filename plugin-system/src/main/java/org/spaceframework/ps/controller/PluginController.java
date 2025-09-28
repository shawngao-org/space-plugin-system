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

package org.spaceframework.ps.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spaceframework.ps.manager.PluginManager;
import org.spaceframework.ps.model.PluginDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller providing comprehensive HTTP endpoints for plugin management operations.
 *
 * <p>This controller exposes a RESTful API for managing plugins within the system, including
 * operations for loading, unloading, reloading, uploading, and querying plugins. It serves as the
 * primary interface for external clients to interact with the plugin system.
 *
 * <p>Available endpoints:
 *
 * <ul>
 *   <li><strong>GET /api/plugins</strong> - Retrieve all loaded plugins
 *   <li><strong>GET /api/plugins/{id}</strong> - Retrieve a specific plugin by ID
 *   <li><strong>POST /api/plugins/load</strong> - Load a plugin from a JAR file path
 *   <li><strong>POST /api/plugins/upload</strong> - Upload and load a plugin JAR file
 *   <li><strong>DELETE /api/plugins/{id}</strong> - Unload a plugin by ID
 *   <li><strong>POST /api/plugins/{id}/reload</strong> - Reload a plugin by ID
 *   <li><strong>GET /api/plugins/status</strong> - Get system status and statistics
 * </ul>
 *
 * <p>All endpoints return JSON responses with appropriate HTTP status codes. Error responses
 * include detailed error messages for debugging purposes.
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see PluginManager
 * @see PluginDescriptor
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginController {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(PluginController.class);

  /** Plugin manager service for handling plugin operations. */
  @Autowired private PluginManager pluginManager;

  /**
   * Retrieves all currently loaded plugins in the system.
   *
   * <p>This endpoint returns a collection of plugin information DTOs containing metadata about all
   * loaded plugins. The response includes plugin ID, name, version, description, author, state,
   * load time, and JAR file path.
   *
   * @return ResponseEntity containing:
   *     <ul>
   *       <li>200 OK with collection of {@link PluginInfo} objects if successful
   *       <li>500 Internal Server Error if an unexpected error occurs
   *     </ul>
   *
   * @see PluginInfo
   * @see PluginManager#getLoadedPlugins()
   */
  @GetMapping
  public ResponseEntity<Collection<PluginInfo>> getAllPlugins() {
    try {
      Collection<PluginDescriptor> plugins = pluginManager.getLoadedPlugins();
      Collection<PluginInfo> pluginInfos = plugins.stream().map(this::toPluginInfo).toList();

      return ResponseEntity.ok(pluginInfos);
    } catch (Exception e) {
      logger.error("Failed to get all plugins", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Retrieves a specific plugin by its unique identifier.
   *
   * <p>This endpoint returns detailed information about a single plugin identified by its unique
   * ID. If the plugin is not found, a 404 Not Found response is returned.
   *
   * @param pluginId the unique identifier of the plugin to retrieve, must not be null
   * @return ResponseEntity containing:
   *     <ul>
   *       <li>200 OK with {@link PluginInfo} object if plugin is found
   *       <li>404 Not Found if no plugin with the specified ID exists
   *       <li>500 Internal Server Error if an unexpected error occurs
   *     </ul>
   *
   * @see PluginInfo
   * @see PluginManager#getPlugin(String)
   */
  @GetMapping("/{pluginId}")
  public ResponseEntity<PluginInfo> getPlugin(@PathVariable String pluginId) {
    try {
      PluginDescriptor descriptor = pluginManager.getPlugin(pluginId);
      if (descriptor == null) {
        return ResponseEntity.notFound().build();
      }

      return ResponseEntity.ok(toPluginInfo(descriptor));
    } catch (Exception e) {
      logger.error("Failed to get plugin: {}", pluginId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Loads a plugin from a specified JAR file path on the server filesystem.
   *
   * <p>This endpoint accepts a JSON request containing the path to a JAR file on the server's
   * filesystem and attempts to load it as a plugin. The JAR file must be accessible to the server
   * and contain a valid plugin implementation.
   *
   * <p>Request body format:
   *
   * <pre>{@code
   * {
   *   "jarPath": "/path/to/plugin.jar"
   * }
   * }</pre>
   *
   * @param request the load plugin request containing the JAR file path, must not be null
   * @return ResponseEntity containing:
   *     <ul>
   *       <li>200 OK with success response and plugin info if loading succeeds
   *       <li>400 Bad Request with error message if loading fails
   *     </ul>
   *
   * @see LoadPluginRequest
   * @see PluginManager#loadPlugin(java.nio.file.Path)
   */
  @PostMapping("/load")
  public ResponseEntity<Map<String, Object>> loadPlugin(@RequestBody LoadPluginRequest request) {
    Map<String, Object> response = new HashMap<>();

    try {
      Path jarPath = Paths.get(request.getJarPath());
      PluginDescriptor descriptor = pluginManager.loadPlugin(jarPath);

      response.put("success", true);
      response.put("message", "Plugin loaded successfully");
      response.put("plugin", toPluginInfo(descriptor));

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Failed to load plugin from: {}", request.getJarPath(), e);

      response.put("success", false);
      response.put("message", "Failed to load plugin: " + e.getMessage());

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * Uploads a plugin JAR file and loads it into the system.
   *
   * <p>This endpoint accepts a multipart file upload containing a plugin JAR file. The uploaded
   * file is saved to the plugins directory and then loaded as a plugin. The file must have a .jar
   * extension and contain a valid plugin implementation.
   *
   * <p>The uploaded file is saved to the configured plugins directory (default: ./plugins) and will
   * replace any existing file with the same name.
   *
   * @param file the multipart file containing the plugin JAR, must not be null or empty
   * @return ResponseEntity containing:
   *     <ul>
   *       <li>200 OK with success response and plugin info if upload and loading succeed
   *       <li>400 Bad Request if file is empty, not a JAR file, or loading fails
   *       <li>500 Internal Server Error if file saving fails
   *     </ul>
   *
   * @see PluginManager#loadPlugin(java.nio.file.Path)
   */
  @PostMapping("/upload")
  public ResponseEntity<Map<String, Object>> uploadPlugin(
      @RequestParam("file") MultipartFile file) {
    Map<String, Object> response = new HashMap<>();

    if (file.isEmpty()) {
      response.put("success", false);
      response.put("message", "No file provided");
      return ResponseEntity.badRequest().body(response);
    }

    if (!Objects.requireNonNull(file.getOriginalFilename()).endsWith(".jar")) {
      response.put("success", false);
      response.put("message", "File must be a JAR file");
      return ResponseEntity.badRequest().body(response);
    }

    try {
      // Save uploaded file to plugins directory
      Path pluginsDir = Paths.get("./plugins");
      if (!Files.exists(pluginsDir)) {
        Files.createDirectories(pluginsDir);
      }

      Path jarPath = pluginsDir.resolve(file.getOriginalFilename());
      Files.copy(file.getInputStream(), jarPath, StandardCopyOption.REPLACE_EXISTING);

      // Load the plugin
      PluginDescriptor descriptor = pluginManager.loadPlugin(jarPath);

      response.put("success", true);
      response.put("message", "Plugin uploaded and loaded successfully");
      response.put("plugin", toPluginInfo(descriptor));

      return ResponseEntity.ok(response);

    } catch (IOException e) {
      logger.error("Failed to save uploaded file", e);
      response.put("success", false);
      response.put("message", "Failed to save uploaded file: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

    } catch (Exception e) {
      logger.error("Failed to load uploaded plugin", e);
      response.put("success", false);
      response.put("message", "Failed to load plugin: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * Unloads a plugin from the system by its unique identifier.
   *
   * <p>This endpoint removes a plugin from the system, performing cleanup operations including
   * calling the plugin's unload method, destroying its Spring context, and releasing resources.
   * Once unloaded, the plugin is no longer available for use.
   *
   * @param pluginId the unique identifier of the plugin to unload, must not be null
   * @return ResponseEntity containing:
   *     <ul>
   *       <li>200 OK with success message if unloading succeeds
   *       <li>404 Not Found if no plugin with the specified ID exists
   *       <li>500 Internal Server Error if unloading fails
   *     </ul>
   *
   * @see PluginManager#unloadPlugin(String)
   */
  @DeleteMapping("/{pluginId}")
  public ResponseEntity<Map<String, Object>> unloadPlugin(@PathVariable String pluginId) {
    Map<String, Object> response = new HashMap<>();

    try {
      boolean success = pluginManager.unloadPlugin(pluginId);

      if (success) {
        response.put("success", true);
        response.put("message", "Plugin unloaded successfully");
        return ResponseEntity.ok(response);
      } else {
        response.put("success", false);
        response.put("message", "Plugin not found or failed to unload");
        return ResponseEntity.notFound().build();
      }

    } catch (Exception e) {
      logger.error("Failed to unload plugin: {}", pluginId, e);
      response.put("success", false);
      response.put("message", "Failed to unload plugin: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
  }

  /**
   * Reloads a plugin by unloading it and loading it again from its JAR file.
   *
   * <p>This endpoint provides hot-swapping functionality by performing a complete reload of the
   * specified plugin. The plugin is first unloaded (including cleanup) and then loaded again from
   * the same JAR file location. This is useful for applying plugin updates without restarting the
   * entire application.
   *
   * <p><strong>Note:</strong> The JAR file should be updated before calling this endpoint to ensure
   * the new version is loaded.
   *
   * @param pluginId the unique identifier of the plugin to reload, must not be null
   * @return ResponseEntity containing:
   *     <ul>
   *       <li>200 OK with success response and updated plugin info if reloading succeeds
   *       <li>400 Bad Request with error message if reloading fails
   *     </ul>
   *
   * @see PluginManager#reloadPlugin(String)
   */
  @PostMapping("/{pluginId}/reload")
  public ResponseEntity<Map<String, Object>> reloadPlugin(@PathVariable String pluginId) {
    Map<String, Object> response = new HashMap<>();

    try {
      PluginDescriptor descriptor = pluginManager.reloadPlugin(pluginId);

      response.put("success", true);
      response.put("message", "Plugin reloaded successfully");
      response.put("plugin", toPluginInfo(descriptor));

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Failed to reload plugin: {}", pluginId, e);
      response.put("success", false);
      response.put("message", "Failed to reload plugin: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * Retrieves the current status and statistics of the plugin system.
   *
   * <p>This endpoint provides an overview of the plugin system including the total number of loaded
   * plugins and detailed information about each plugin's current state. This is useful for
   * monitoring and administrative purposes.
   *
   * <p>Response format:
   *
   * <pre>{@code
   * {
   *   "totalPlugins": 3,
   *   "plugins": [
   *     {
   *       "id": "example-plugin",
   *       "name": "Example Plugin",
   *       "state": "LOADED",
   *       "loadedAt": "2023-12-01T10:30:00"
   *     }
   *   ]
   * }
   * }</pre>
   *
   * @return ResponseEntity containing:
   *     <ul>
   *       <li>200 OK with system status information if successful
   *       <li>500 Internal Server Error if an unexpected error occurs
   *     </ul>
   *
   * @see PluginManager#getLoadedPluginCount()
   * @see PluginManager#getLoadedPlugins()
   */
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getStatus() {
    Map<String, Object> status = new HashMap<>();

    try {
      status.put("totalPlugins", pluginManager.getLoadedPluginCount());
      status.put(
          "plugins",
          pluginManager.getLoadedPlugins().stream()
              .map(
                  descriptor ->
                      Map.of(
                          "id", descriptor.getPluginId(),
                          "name", descriptor.getName(),
                          "state", descriptor.getState().toString(),
                          "loadedAt", descriptor.getLoadedAt().toString()))
              .toList());

      return ResponseEntity.ok(status);

    } catch (Exception e) {
      logger.error("Failed to get plugin system status", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Converts a PluginDescriptor to a PluginInfo DTO for API responses.
   *
   * <p>This private utility method transforms internal plugin descriptor objects into data transfer
   * objects suitable for JSON serialization and API responses. It extracts relevant information
   * while hiding internal implementation details.
   *
   * @param descriptor the plugin descriptor to convert, must not be null
   * @return a new PluginInfo DTO containing the descriptor's information
   * @see PluginDescriptor
   * @see PluginInfo
   */
  private PluginInfo toPluginInfo(PluginDescriptor descriptor) {
    return new PluginInfo(
        descriptor.getPluginId(),
        descriptor.getName(),
        descriptor.getVersion(),
        descriptor.getDescription(),
        descriptor.getAuthor(),
        descriptor.getState().toString(),
        descriptor.getLoadedAt().toString(),
        descriptor.getJarPath().toString());
  }

  /**
   * Data Transfer Object (DTO) representing plugin information for API responses.
   *
   * <p>This immutable class encapsulates plugin metadata in a format suitable for JSON
   * serialization and client consumption. It provides a clean interface for exposing plugin
   * information without revealing internal implementation details.
   *
   * <p>All fields are final and set through the constructor, ensuring immutability and thread
   * safety. The class includes standard getter methods for JSON serialization frameworks.
   *
   * @param id The unique identifier of the plugin.
   * @param name The human-readable name of the plugin.
   * @param version The version string of the plugin.
   * @param description The description of the plugin's functionality.
   * @param author The author(s) of the plugin.
   * @param state The current state of the plugin as a string.
   * @param loadedAt The timestamp when the plugin was loaded, as a string.
   * @param jarPath The file system path to the plugin's JAR file.
   * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
   * @version 1.0.0-SNAPSHOT
   * @since 1.0.0
   */
  public record PluginInfo(
      String id,
      String name,
      String version,
      String description,
      String author,
      String state,
      String loadedAt,
      String jarPath) {

    /**
     * Constructs a new PluginInfo with the specified metadata.
     *
     * @param id the unique identifier of the plugin, must not be null
     * @param name the human-readable name of the plugin, must not be null
     * @param version the version string of the plugin, must not be null
     * @param description the description of the plugin's functionality, may be null
     * @param author the author(s) of the plugin, may be null
     * @param state the current state of the plugin as a string, must not be null
     * @param loadedAt the timestamp when the plugin was loaded, must not be null
     * @param jarPath the file system path to the plugin's JAR file, must not be null
     */
    public PluginInfo {}

    // Getters

    /** Returns the plugin ID. @return the plugin ID */
    @Override
    public String id() {
      return id;
    }

    /** Returns the plugin name. @return the plugin name */
    @Override
    public String name() {
      return name;
    }

    /** Returns the plugin version. @return the plugin version */
    @Override
    public String version() {
      return version;
    }

    /** Returns the plugin description. @return the plugin description */
    @Override
    public String description() {
      return description;
    }

    /** Returns the plugin author. @return the plugin author */
    @Override
    public String author() {
      return author;
    }

    /** Returns the plugin state. @return the plugin state as a string */
    @Override
    public String state() {
      return state;
    }

    /** Returns the plugin load timestamp. @return the formatted load timestamp */
    @Override
    public String loadedAt() {
      return loadedAt;
    }

    /** Returns the plugin JAR file path. @return the JAR file path */
    @Override
    public String jarPath() {
      return jarPath;
    }
  }

  /**
   * Data Transfer Object (DTO) for plugin loading requests.
   *
   * <p>This class represents the request body for loading plugins from a specified JAR file path.
   * It provides a structured way to accept plugin loading parameters via HTTP requests.
   *
   * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
   * @version 1.0.0-SNAPSHOT
   * @since 1.0.0
   */
  public static class LoadPluginRequest {
    /** The file system path to the JAR file to load as a plugin. */
    private String jarPath;

    /**
     * Returns the JAR file path.
     *
     * @return the JAR file path, may be null
     */
    public String getJarPath() {
      return jarPath;
    }

    /**
     * Sets the JAR file path.
     *
     * @param jarPath the JAR file path to set
     */
    public void setJarPath(String jarPath) {
      this.jarPath = jarPath;
    }
  }
}
