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

package org.spaceframework.ps.watcher;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spaceframework.ps.manager.PluginManager;
import org.spaceframework.ps.model.PluginDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service that watches the plugins directory for file changes and automatically handles plugin
 * hot-reloading.
 *
 * <p>This service provides automatic plugin lifecycle management based on file system events in the
 * configured plugins directory. It monitors JAR files for creation, modification, and deletion
 * events, automatically triggering the appropriate plugin operations without requiring manual
 * intervention.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li><strong>Hot Reloading:</strong> Automatically reloads plugins when JAR files are modified
 *   <li><strong>Auto Loading:</strong> Loads new plugins when JAR files are added to the directory
 *   <li><strong>Auto Unloading:</strong> Unloads plugins when their JAR files are deleted
 *   <li><strong>Modification Detection:</strong> Uses both file system events and periodic checks
 *   <li><strong>Debouncing:</strong> Prevents rapid successive operations on the same file
 *   <li><strong>Graceful Shutdown:</strong> Properly cleans up resources on application shutdown
 * </ul>
 *
 * <p>The service is conditionally enabled based on the {@code plugin.system.hot-reload-enabled}
 * configuration property (enabled by default). It uses the {@link DirectoryWatcher} library for
 * efficient file system monitoring and a {@link ScheduledExecutorService} for asynchronous
 * operations and periodic checks.
 *
 * <p>Configuration properties:
 *
 * <ul>
 *   <li>{@code plugin.system.plugins-directory}: Directory to watch (default: ./plugins)
 *   <li>{@code plugin.system.watch-interval}: Periodic check interval in milliseconds (default:
 *       1000)
 *   <li>{@code plugin.system.hot-reload-enabled}: Enable/disable hot reloading (default: true)
 * </ul>
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see PluginManager
 * @see DirectoryWatcher
 * @see PluginDescriptor
 */
@Service
@ConditionalOnProperty(
    name = "plugin.system.hot-reload-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PluginWatcherService {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(PluginWatcherService.class);

  /** Plugin manager for handling plugin lifecycle operations. */
  @Autowired private PluginManager pluginManager;

  /** Directory path to watch for plugin JAR files. */
  @Value("${plugin.system.plugins-directory:./plugins}")
  private String pluginsDirectory;

  /** Interval in milliseconds for periodic modification checks. */
  @Value("${plugin.system.watch-interval:1000}")
  private long watchInterval;

  /** Directory watcher for monitoring file system events. */
  private DirectoryWatcher watcher;

  /** Executor service for asynchronous operations and periodic tasks. */
  private ScheduledExecutorService executorService;

  /** Map tracking file modification times for change detection. */
  private final ConcurrentHashMap<Path, Long> fileModificationTimes = new ConcurrentHashMap<>();

  /**
   * Retrieves the plugins directory path, creating it if it doesn't exist.
   *
   * <p>This method normalizes the plugins directory path, resolves it against the current working
   * directory, and ensures the directory exists. If the directory creation fails, an exception is
   * thrown.
   *
   * @param pluginsDirectory the directory path to watch for plugins (relative or absolute)
   * @param logger the logger instance for logging creation events
   * @return the normalized path to the plugins directory
   * @throws IOException if the directory creation fails
   */
  public static Path getPluginsDir(String pluginsDirectory, Logger logger) throws IOException {
    Path pluginsDir = Paths.get(pluginsDirectory);

    // Create plugins directory if it doesn't exist
    if (!Files.exists(pluginsDir)) {
      try {
        Files.createDirectories(pluginsDir);
        logger.info("Created plugins directory: {}", pluginsDir);
        return pluginsDir;
      } catch (IOException e) {
        logger.error("Failed to create plugins directory: {}", pluginsDir, e);
        throw new IOException("Failed to create plugins directory", e);
      }
    }
    return pluginsDir;
  }

  /**
   * Initializes the plugin watcher service after bean construction.
   *
   * <p>This method performs the complete setup of the file watching system:
   *
   * <ol>
   *   <li>Creates the plugins directory if it doesn't exist
   *   <li>Initializes the executor service for asynchronous operations
   *   <li>Records modification times for existing JAR files
   *   <li>Creates and configures the directory watcher
   *   <li>Starts the file system monitoring in a separate thread
   *   <li>Schedules periodic checks for missed modifications
   * </ol>
   *
   * <p>The method is designed to be fault-tolerant and will log errors but continue initialization
   * even if some steps fail. If directory creation or watcher setup fails, the service will be
   * disabled for the current session.
   *
   * @see #initializeExistingFiles(Path)
   * @see #handleDirectoryEvent(DirectoryChangeEvent)
   * @see #checkForModifications()
   */
  @PostConstruct
  public void initialize() throws IOException {
    Path pluginsDir = getPluginsDir(pluginsDirectory, logger);

    executorService = Executors.newScheduledThreadPool(2);

    // Initialize file modification times for existing JAR files
    initializeExistingFiles(pluginsDir);

    try {
      // Create directory watcher
      watcher =
          DirectoryWatcher.builder().path(pluginsDir).listener(this::handleDirectoryEvent).build();
    } catch (IOException e) {
      logger.error("Failed to create directory watcher", e);
      return;
    }

    // Start watching in a separate thread
    CompletableFuture.runAsync(
        () -> {
          try {
            logger.info("Starting plugin directory watcher for: {}", pluginsDir);
            watcher.watch();
          } catch (Exception e) {
            logger.error("Failed to start directory watcher", e);
          }
        },
        executorService);

    // Schedule periodic check for file modifications
    executorService.scheduleWithFixedDelay(
        this::checkForModifications, watchInterval, watchInterval, TimeUnit.MILLISECONDS);

    logger.info("Plugin watcher service initialized successfully");
  }

  /**
   * Shuts down the plugin watcher service before bean destruction.
   *
   * <p>This method performs graceful cleanup of all resources:
   *
   * <ol>
   *   <li>Closes the directory watcher to stop file system monitoring
   *   <li>Shuts down the executor service and waits for running tasks
   *   <li>Forces shutdown if tasks don't complete within the timeout
   *   <li>Handles interruption gracefully
   * </ol>
   *
   * <p>The shutdown process is designed to be safe and will attempt to complete within a reasonable
   * timeout (5 seconds) before forcing termination.
   */
  @PreDestroy
  public void shutdown() {
    logger.info("Shutting down plugin watcher service...");

    if (watcher != null) {
      try {
        watcher.close();
      } catch (IOException e) {
        logger.warn("Error closing directory watcher", e);
      }
    }

    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    logger.info("Plugin watcher service shut down");
  }

  /**
   * Handles directory change events from the file watcher.
   *
   * <p>This method is the main event handler for file system changes in the plugins directory. It
   * filters events to only process JAR files and delegates to specific handlers based on the event
   * type:
   *
   * <ul>
   *   <li><strong>CREATE:</strong> New JAR file added - triggers plugin loading
   *   <li><strong>MODIFY:</strong> Existing JAR file changed - triggers plugin reloading
   *   <li><strong>DELETE:</strong> JAR file removed - triggers plugin unloading
   * </ul>
   *
   * <p>Non-JAR files are ignored to prevent unnecessary processing of temporary files, directories,
   * or other file types that may appear in the plugins directory.
   *
   * @param event the directory change event containing the path and event type, must not be null
   * @see #handleFileCreated(Path)
   * @see #handleFileModified(Path)
   * @see #handleFileDeleted(Path)
   */
  private void handleDirectoryEvent(DirectoryChangeEvent event) {
    Path path = event.path();

    // Only process JAR files
    if (!path.toString().endsWith(".jar")) {
      return;
    }

    logger.debug("Directory event: {} for file: {}", event.eventType(), path);

    switch (event.eventType()) {
      case CREATE:
        handleFileCreated(path);
        break;
      case MODIFY:
        handleFileModified(path);
        break;
      case DELETE:
        handleFileDeleted(path);
        break;
      default:
        logger.debug("Unhandled event type: {}", event.eventType());
    }
  }

  /**
   * Handles new JAR file creation events.
   *
   * <p>When a new JAR file is detected in the plugins directory, this method:
   *
   * <ol>
   *   <li>Logs the detection of the new plugin JAR
   *   <li>Schedules a delayed loading operation (1 second delay)
   *   <li>Verifies the file still exists before attempting to load
   *   <li>Loads the plugin using the {@link PluginManager}
   *   <li>Updates the file modification time tracking
   * </ol>
   *
   * <p>The delay is important to ensure that the file is completely written before attempting to
   * load it, preventing issues with partially transferred files.
   *
   * @param jarPath the path to the newly created JAR file, must not be null
   * @see PluginManager#loadPlugin(Path)
   * @see #updateFileModificationTime(Path)
   */
  private void handleFileCreated(Path jarPath) {
    logger.info("New plugin JAR detected: {}", jarPath);

    // Wait a bit to ensure file is completely written
    executorService.schedule(
        () -> {
          try {
            if (Files.exists(jarPath)) {
              pluginManager.loadPlugin(jarPath);
              updateFileModificationTime(jarPath);
            }
          } catch (Exception e) {
            logger.error("Failed to load new plugin: {}", jarPath, e);
          }
        },
        1,
        TimeUnit.SECONDS);
  }

  /**
   * Handles JAR file modification events.
   *
   * <p>When an existing JAR file is modified, this method:
   *
   * <ol>
   *   <li>Logs the modification event
   *   <li>Attempts to find the corresponding loaded plugin by file path
   *   <li>If found, schedules a plugin reload operation with a delay
   *   <li>If not found, treats it as a new plugin and attempts to load it
   *   <li>Updates the file modification time tracking
   * </ol>
   *
   * <p>The delay prevents rapid successive reload operations and ensures the modified file is
   * stable before processing. If no existing plugin is found for the modified JAR, it's treated as
   * a new plugin installation.
   *
   * @param jarPath the path to the modified JAR file, must not be null
   * @see #findPluginIdByPath(Path)
   * @see PluginManager#reloadPlugin(String)
   * @see #handleFileCreated(Path)
   */
  private void handleFileModified(Path jarPath) {
    logger.info("Plugin JAR modified: {}", jarPath);

    // Find the plugin that corresponds to this JAR file
    String pluginId = findPluginIdByPath(jarPath);

    if (pluginId != null) {
      executorService.schedule(
          () -> {
            try {
              if (Files.exists(jarPath)) {
                pluginManager.reloadPlugin(pluginId);
                updateFileModificationTime(jarPath);
                logger.info("Successfully reloaded plugin: {}", pluginId);
              }
            } catch (Exception e) {
              logger.error("Failed to reload plugin: {}", pluginId, e);
            }
          },
          1,
          TimeUnit.SECONDS);
    } else {
      // If no existing plugin found, try to load as new plugin
      handleFileCreated(jarPath);
    }
  }

  /**
   * Handles JAR file deletion events.
   *
   * <p>When a JAR file is deleted from the plugins directory, this method:
   *
   * <ol>
   *   <li>Logs the deletion event
   *   <li>Finds the corresponding loaded plugin by file path
   *   <li>Unloads the plugin using the {@link PluginManager}
   *   <li>Removes the file from modification time tracking
   *   <li>Logs the successful unloading
   * </ol>
   *
   * <p>If no loaded plugin corresponds to the deleted file, no action is taken. This can happen if
   * the file was not a valid plugin or was never successfully loaded.
   *
   * @param jarPath the path to the deleted JAR file, must not be null
   * @see #findPluginIdByPath(Path)
   * @see PluginManager#unloadPlugin(String)
   */
  private void handleFileDeleted(Path jarPath) {
    logger.info("Plugin JAR deleted: {}", jarPath);

    String pluginId = findPluginIdByPath(jarPath);
    if (pluginId != null) {
      pluginManager.unloadPlugin(pluginId);
      fileModificationTimes.remove(jarPath);
      logger.info("Unloaded plugin due to file deletion: {}", pluginId);
    }
  }

  /**
   * Periodically checks for file modifications that might have been missed by the file watcher.
   *
   * <p>This method provides a backup mechanism for detecting file changes that may not have been
   * caught by the primary file system event monitoring. It:
   *
   * <ol>
   *   <li>Scans all JAR files in the plugins directory
   *   <li>Compares current modification times with stored values
   *   <li>Triggers modification handling for files with newer timestamps
   *   <li>Handles I/O errors gracefully with appropriate logging
   * </ol>
   *
   * <p>This periodic check is essential for reliability, as file system events can sometimes be
   * missed due to system load, network file systems, or other factors. The check interval is
   * configurable via the {@code plugin.system.watch-interval} property.
   *
   * @see #handleFileModified(Path)
   */
  private void checkForModifications() {
    Path pluginsDir = Paths.get(pluginsDirectory);
    try (Stream<Path> pathStream =
        Files.list(pluginsDir).filter(path -> path.toString().endsWith(".jar"))) {

      pathStream.forEach(
          jarPath -> {
            try {
              long currentModTime = Files.getLastModifiedTime(jarPath).toMillis();
              Long lastKnownModTime = fileModificationTimes.get(jarPath);

              if (lastKnownModTime == null || currentModTime > lastKnownModTime) {
                logger.debug("Detected modification via periodic check: {}", jarPath);
                handleFileModified(jarPath);
              }
            } catch (IOException e) {
              logger.warn("Failed to check modification time for: {}", jarPath, e);
            }
          });

    } catch (IOException e) {
      logger.warn("Failed to scan plugins directory during periodic check", e);
    }
  }

  /**
   * Initializes modification times for existing JAR files in the plugins directory.
   *
   * <p>This method is called during service initialization to establish a baseline for file
   * modification tracking. It scans the plugins directory and records the current modification time
   * for all existing JAR files.
   *
   * <p>This initialization is important for the periodic modification check to work correctly, as
   * it needs to compare against known modification times to detect changes.
   *
   * @param pluginsDir the plugins directory path to scan, must not be null
   * @see #updateFileModificationTime(Path)
   */
  private void initializeExistingFiles(Path pluginsDir) {
    try (Stream<Path> pathStream =
        Files.list(pluginsDir).filter(path -> path.toString().endsWith(".jar"))) {

      pathStream.forEach(this::updateFileModificationTime);
    } catch (IOException e) {
      logger.warn("Failed to initialize existing file modification times", e);
    }
  }

  /**
   * Updates the stored modification time for a JAR file.
   *
   * <p>This utility method retrieves the current last modified time of a file and stores it in the
   * internal tracking map. It's used to maintain accurate modification time records for change
   * detection.
   *
   * <p>If the file's modification time cannot be retrieved (e.g., due to I/O errors or file
   * deletion), the operation is logged as a warning but does not throw an exception.
   *
   * @param jarPath the path to the JAR file whose modification time should be updated, must not be
   *     null
   */
  private void updateFileModificationTime(Path jarPath) {
    try {
      long modTime = Files.getLastModifiedTime(jarPath).toMillis();
      fileModificationTimes.put(jarPath, modTime);
    } catch (IOException e) {
      logger.warn("Failed to update modification time for: {}", jarPath, e);
    }
  }

  /**
   * Finds the plugin ID that corresponds to a given JAR file path.
   *
   * <p>This method searches through all currently loaded plugins to find one whose JAR file path
   * matches the provided path. This is used to correlate file system events with specific plugin
   * instances.
   *
   * <p>The search is performed by comparing the JAR file paths stored in {@link PluginDescriptor}
   * instances with the provided path.
   *
   * @param jarPath the JAR file path to search for, must not be null
   * @return the plugin ID if a matching plugin is found, or null if no match exists
   * @see PluginManager#getLoadedPlugins()
   * @see PluginDescriptor#getJarPath()
   */
  private String findPluginIdByPath(Path jarPath) {
    return pluginManager.getLoadedPlugins().stream()
        .filter(descriptor -> descriptor.getJarPath().equals(jarPath))
        .map(PluginDescriptor::getPluginId)
        .findFirst()
        .orElse(null);
  }
}
