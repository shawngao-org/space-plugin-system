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

package org.spaceframework.ps.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spaceframework.ps.manager.PluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the plugin system initialization.
 *
 * <p>This configuration class implements {@link CommandLineRunner} to perform plugin system
 * initialization tasks during application startup. It is responsible for loading all existing
 * plugins from the configured plugins directory and ensuring the plugin system is ready for
 * operation.
 *
 * <p>The initialization process includes:
 *
 * <ul>
 *   <li>Loading all existing plugin JAR files from the plugins directory
 *   <li>Validating plugin metadata and dependencies
 *   <li>Creating isolated Spring contexts for each plugin
 *   <li>Registering plugin beans and endpoints
 * </ul>
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see PluginManager
 * @see CommandLineRunner
 */
@Configuration
public class PluginSystemConfig implements CommandLineRunner {

  /** Logger instance for this class. */
  private static final Logger logger = LoggerFactory.getLogger(PluginSystemConfig.class);

  /** The plugin manager service for handling plugin operations. */
  @Autowired private PluginManager pluginManager;

  /**
   * Executes the plugin system initialization process.
   *
   * <p>This method is called automatically by Spring Boot after the application context has been
   * fully initialized. It loads all existing plugins from the configured plugins directory and
   * prepares the system for plugin operations.
   *
   * <p>If any errors occur during initialization, they are logged but do not prevent the
   * application from starting. This ensures that plugin system failures do not bring down the
   * entire application.
   *
   * @param args command line arguments passed to the application (not used)
   */
  @Override
  public void run(String... args) {
    logger.info("Initializing plugin system...");

    try {
      // Load all existing plugins from the plugins directory
      pluginManager.loadAllPlugins();

      int loadedCount = pluginManager.getLoadedPluginCount();
      logger.info("Plugin system initialized successfully. Loaded {} plugins.", loadedCount);

    } catch (Exception e) {
      logger.error("Failed to initialize plugin system", e);
    }
  }
}
