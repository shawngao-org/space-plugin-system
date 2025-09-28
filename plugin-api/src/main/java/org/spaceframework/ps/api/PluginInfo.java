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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AliasFor;

/**
 * Annotation to provide comprehensive metadata about a plugin.
 *
 * <p>This annotation should be placed on the main plugin class to provide essential information
 * about the plugin including its identity, version, description, and Spring configuration details.
 * The annotation combines plugin metadata with Spring's {@link ComponentScan} and {@link
 * Configuration} annotations to enable automatic bean discovery and configuration.
 *
 * <p>The annotation serves multiple purposes:
 *
 * <ul>
 *   <li>Provides plugin identification and metadata
 *   <li>Configures Spring component scanning for the plugin
 *   <li>Enables Spring configuration processing
 *   <li>Specifies plugin system compatibility requirements
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @PluginInfo(
 *     id = "example-plugin",
 *     name = "Example Plugin",
 *     version = "1.0.0",
 *     description = "A sample plugin demonstrating the plugin system",
 *     author = "Plugin Developer",
 *     scanBasePackages = {"com.example.plugin"}
 * )
 * public class ExamplePlugin implements Plugin {
 *     // Plugin implementation
 * }
 * }</pre>
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see Plugin
 * @see ComponentScan
 * @see Configuration
 */
@ComponentScan(
    excludeFilters = {
      @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
    })
@Configuration
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginInfo {

  /**
   * The unique identifier for the plugin.
   *
   * <p>This identifier must be unique across all plugins in the system. It is used internally by
   * the plugin system to track and manage plugin instances. The ID should follow a naming
   * convention similar to Java package names (e.g., "com.example.my-plugin").
   *
   * @return the unique plugin identifier
   */
  String id();

  /**
   * The human-readable name of the plugin.
   *
   * <p>This is the display name that will be shown to users in management interfaces and logs. It
   * should be descriptive and user-friendly.
   *
   * @return the plugin display name
   */
  String name();

  /**
   * The version of the plugin.
   *
   * <p>The version should follow semantic versioning conventions (e.g., "1.0.0"). This is used for
   * compatibility checking and plugin management operations.
   *
   * @return the plugin version string
   */
  String version();

  /**
   * A description of what the plugin does.
   *
   * <p>This should provide a clear, concise explanation of the plugin's functionality and purpose.
   * It may be displayed in plugin management interfaces to help users understand what the plugin
   * provides.
   *
   * @return the plugin description, or empty string if not specified
   */
  String description() default "";

  /**
   * The author(s) of the plugin.
   *
   * <p>This field can contain the name of the individual developer, organization, or team
   * responsible for creating and maintaining the plugin.
   *
   * @return the plugin author(s), defaults to "Unknown" if not specified
   */
  String author() default "Unknown";

  /**
   * The minimum required version of the plugin system.
   *
   * <p>This specifies the minimum version of the plugin system framework that is required for this
   * plugin to function correctly. The plugin system will check this compatibility requirement
   * during plugin loading.
   *
   * @return the required system version, defaults to "1.0.0-SNAPSHOT"
   */
  String requiredSystemVersion() default "1.0.0-SNAPSHOT";

  /**
   * Base packages to scan for Spring components within the plugin.
   *
   * <p>This is an alias for {@link ComponentScan#basePackages()}. It specifies which packages
   * should be scanned for Spring components (services, controllers, etc.) when the plugin is
   * loaded. If not specified, the package of the annotated class will be used as the base package.
   *
   * @return array of base package names to scan
   * @see ComponentScan#basePackages()
   */
  @AliasFor(annotation = ComponentScan.class, attribute = "basePackages")
  String[] scanBasePackages() default {};

  /**
   * The bean name generator to use for component scanning.
   *
   * <p>This is an alias for {@link ComponentScan#nameGenerator()}. It allows customization of how
   * Spring generates bean names for discovered components within the plugin.
   *
   * @return the bean name generator class
   * @see ComponentScan#nameGenerator()
   */
  @AliasFor(annotation = ComponentScan.class, attribute = "nameGenerator")
  Class<? extends BeanNameGenerator> nameGenerator() default BeanNameGenerator.class;

  /**
   * Whether to proxy bean methods for configuration classes.
   *
   * <p>This is an alias for {@link Configuration#proxyBeanMethods()}. When set to true (default),
   * Spring will create CGLIB proxies for configuration classes to ensure singleton semantics for
   * bean methods.
   *
   * @return true if bean methods should be proxied, false otherwise
   * @see Configuration#proxyBeanMethods()
   */
  @AliasFor(annotation = Configuration.class)
  boolean proxyBeanMethods() default true;
}
