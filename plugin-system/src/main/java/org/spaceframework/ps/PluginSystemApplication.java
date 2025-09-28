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

package org.spaceframework.ps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for the Space Plugin System.
 *
 * <p>This is the entry point for the Spring Boot application that provides a dynamic plugin
 * management system. The application supports loading, unloading, and hot-reloading of plugins at
 * runtime.
 *
 * <p>Key features include:
 *
 * <ul>
 *   <li>Dynamic plugin loading from JAR files
 *   <li>Plugin isolation using custom ClassLoaders
 *   <li>Spring context integration for plugins
 *   <li>RESTful API for plugin management
 *   <li>File system watching for hot-reload capabilities
 * </ul>
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 */
@SpringBootApplication
@EnableAsync
public class PluginSystemApplication {

  /**
   * Main method to start the Spring Boot application.
   *
   * <p>This method initializes the Spring application context and starts the embedded web server.
   * The plugin system will be automatically initialized during the application startup process.
   *
   * @param args command line arguments passed to the application
   */
  public static void main(String[] args) {
    SpringApplication.run(PluginSystemApplication.class, args);
  }
}
