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

package org.spaceframework.ps.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spaceframework.ps.api.PluginInfo;
import org.spaceframework.ps.model.PluginDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Manages Spring contexts for plugins and integrates plugin beans with the main application
 * context.
 *
 * <p>This service is responsible for creating isolated Spring application contexts for each plugin
 * while ensuring proper integration with the main application context. It handles the complete
 * lifecycle of plugin Spring contexts including creation, bean registration, controller mapping,
 * and cleanup during plugin unloading.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li><strong>Context Management:</strong> Creates and manages separate Spring contexts for each
 *       plugin
 *   <li><strong>Bean Integration:</strong> Registers plugin beans in the main application context
 *   <li><strong>Controller Registration:</strong> Automatically registers plugin controllers with
 *       Spring MVC
 *   <li><strong>Resource Cleanup:</strong> Properly destroys plugin contexts and unregisters
 *       resources
 *   <li><strong>Component Scanning:</strong> Scans plugin packages for Spring components
 * </ul>
 *
 * <p>The manager creates a parent-child relationship between the main application context and
 * plugin contexts, allowing plugins to access main application beans while maintaining isolation
 * for their own components.
 *
 * <p>Plugin controllers are automatically detected and their request mappings are registered with
 * the main application's {@link RequestMappingHandlerMapping}, enabling plugin endpoints to be
 * accessible through the main application's web layer.
 *
 * @author <a href="mailto:shawngao.org@outlook.com">ZetoHkr</a>
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 * @see PluginDescriptor
 * @see PluginInfo
 * @see AnnotationConfigApplicationContext
 * @see RequestMappingHandlerMapping
 */
@Service
public class PluginSpringContextManager {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(PluginSpringContextManager.class);

  /** The main application context that serves as parent for plugin contexts. */
  @Autowired private ApplicationContext mainApplicationContext;

  /** Handler mapping for registering plugin controller endpoints. */
  @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;

  /** Map storing plugin contexts by plugin ID. */
  private final Map<String, AnnotationConfigApplicationContext> pluginContexts =
      new ConcurrentHashMap<>();

  /**
   * Gets all plugin IDs that have active Spring contexts.
   *
   * <p>This method returns a snapshot of all plugins that currently have Spring contexts managed by
   * this service. The returned set is a copy and modifications to it will not affect the internal
   * state.
   *
   * @return a set containing all plugin IDs with active Spring contexts, never null
   */
  public Set<String> getPluginIds() {
    return new HashSet<>(pluginContexts.keySet());
  }

  /**
   * Creates and initializes a Spring context for a plugin.
   *
   * <p>This method performs the complete setup of a Spring application context for a plugin:
   *
   * <ol>
   *   <li>Creates a new {@link AnnotationConfigApplicationContext} with the main context as parent
   *   <li>Sets the plugin's ClassLoader for proper class loading isolation
   *   <li>Scans packages specified in the {@link PluginInfo} annotation for Spring components
   *   <li>Registers the plugin class as a configuration class
   *   <li>Refreshes the context to initialize all beans
   *   <li>Registers plugin beans in the main application context
   *   <li>Processes and registers plugin controllers with the main application's request mapping
   * </ol>
   *
   * <p>The method uses the {@link PluginInfo} annotation on the plugin class to determine which
   * packages to scan for components. If no packages are specified, it defaults to scanning the
   * plugin class's package.
   *
   * <p>Plugin controllers (classes annotated with {@link Controller} or {@link RestController}) are
   * automatically detected and their request mappings are registered with the main application's
   * {@link RequestMappingHandlerMapping}.
   *
   * @param descriptor the plugin descriptor containing plugin metadata and instance, must not be
   *     null
   * @throws RuntimeException if context creation fails due to configuration errors or bean
   *     conflicts
   * @see PluginInfo#scanBasePackages()
   * @see #processController(String, Object, boolean)
   */
  public void createPluginContext(PluginDescriptor descriptor) {
    String pluginId = descriptor.getPluginId();
    try {
      logger.info("Creating Spring context for plugin: {}", pluginId);
      // Create a new application context for the plugin
      AnnotationConfigApplicationContext pluginContext = new AnnotationConfigApplicationContext();
      pluginContext.setParent(mainApplicationContext);
      pluginContext.setClassLoader(descriptor.getClassLoader());
      // Scan for components in the plugin
      Class<?> pluginClass = descriptor.getPluginInstance().getClass();
      PluginInfo pluginInfo = pluginClass.getAnnotation(PluginInfo.class);
      if (pluginInfo != null) {
        // Get base packages from @ComponentScan annotation
        String[] basePackages = pluginInfo.scanBasePackages();
        if (basePackages.length == 0) {
          // If no base packages specified, use the plugin class package
          basePackages = new String[] {pluginClass.getPackage().getName()};
        }
        logger.info(
            "Scanning packages for plugin {}: {}", pluginId, String.join(", ", basePackages));
        // Scan for components in the specified packages first
        for (String basePackage : basePackages) {
          pluginContext.scan(basePackage);
        }
      }
      // Register the plugin class as a configuration class
      pluginContext.register(pluginClass);
      // Refresh the context to initialize all beans
      pluginContext.refresh();
      // Log all registered beans for debugging
      String[] beanNames = pluginContext.getBeanDefinitionNames();
      logger.info(
          "Plugin {} loaded {} beans: {}",
          pluginId,
          beanNames.length,
          String.join(", ", beanNames));
      // Manually register beans from the plugin context into the main application context
      if (mainApplicationContext
          instanceof ConfigurableApplicationContext configurableMainContext) {
        // Ensure beans are properly registered in the main context
        for (String beanName : beanNames) {
          if (!configurableMainContext.containsBean(beanName)) {
            logger.info(
                "Registering bean {} from plugin {} to the main context", beanName, pluginId);
            Object bean = pluginContext.getBean(beanName);
            GenericBeanDefinition beanDefinition = getGenericBeanDefinition(bean);
            BeanDefinitionRegistry registry =
                (BeanDefinitionRegistry) configurableMainContext.getBeanFactory();
            // Register the Bean definition in the context
            registry.registerBeanDefinition(beanName, beanDefinition);
            // Manually register each bean
            configurableMainContext.getAutowireCapableBeanFactory().autowireBean(bean);
            configurableMainContext.getBeanFactory().registerSingleton(beanName, bean);
            processController(beanName, bean, false);
          }
        }
      } else {
        logger.error(
            "mainApplicationContext is not an instance of ConfigurableApplicationContext. "
                + "Cannot register beans.");
      }
      // Store the plugin context for later use
      pluginContexts.put(pluginId, pluginContext);
      logger.info("Successfully created Spring context for plugin: {}", pluginId);
    } catch (Exception e) {
      logger.error("Failed to create Spring context for plugin: {}", pluginId, e);
      throw new RuntimeException("Failed to create plugin Spring context", e);
    }
  }

  /**
   * Processes a plugin bean to determine if it's a controller and registers its mappings.
   *
   * <p>This method checks if the provided bean is a Spring MVC controller (annotated with {@link
   * Controller} or {@link RestController}) and processes its request mappings. If the bean is a
   * controller, all its methods are examined for mapping annotations and registered with the main
   * application's request handler.
   *
   * <p>The method handles both class-level and method-level {@link RequestMapping} annotations,
   * combining their paths appropriately. If no class-level mapping exists, an empty root path is
   * used.
   *
   * @param beanName the name of the bean in the Spring context, must not be null
   * @param bean the bean instance to process, must not be null
   * @param reverseRegister if {@code true}, unregisters the mappings instead of registering them
   * @see #isController(Object)
   * @see #processControllerMethods(Object, String, String, boolean)
   */
  private void processController(String beanName, Object bean, boolean reverseRegister) {
    if (isController(bean)) {
      logger.info("Processing rest controller {} to main application's HandlerMapping", beanName);
      if (bean.getClass().isAnnotationPresent(RequestMapping.class)) {
        RequestMapping classRequestMapping = bean.getClass().getAnnotation(RequestMapping.class);
        String[] classPaths = classRequestMapping.value();
        for (String classPath : classPaths) {
          processControllerMethods(
              bean, beanName, classPath != null ? classPath : "", reverseRegister);
        }
      } else {
        // No class-level @RequestMapping, use empty root path
        processControllerMethods(bean, beanName, "", reverseRegister);
      }
    }
  }

  /**
   * Creates a generic bean definition for registering a plugin bean in the main context.
   *
   * <p>This utility method creates a {@link GenericBeanDefinition} configured for automatic
   * dependency injection. The bean definition is set up with:
   *
   * <ul>
   *   <li>The bean's actual class as the bean class
   *   <li>Autowire mode set to by-type for automatic dependency injection
   *   <li>Singleton scope (default Spring scope)
   * </ul>
   *
   * @param bean the bean instance for which to create a definition, must not be null
   * @return a configured GenericBeanDefinition for the bean, never null
   */
  private static GenericBeanDefinition getGenericBeanDefinition(Object bean) {
    Class<?> clazz = bean.getClass();
    // Create a BeanDefinition for the plugin class
    GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
    beanDefinition.setBeanClass(clazz);
    beanDefinition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);
    // Optionally set scope or other properties of the bean definition
    beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON); // Default is singleton
    return beanDefinition;
  }

  /**
   * Checks if a bean is a controller (has @Controller or @RestController annotation).
   *
   * @param bean the bean instance to check, must not be null
   * @return {@code true} if the bean is a controller, {@code false} otherwise
   */
  private boolean isController(Object bean) {
    Class<?> clazz = bean.getClass();
    return clazz.isAnnotationPresent(Controller.class)
        || clazz.isAnnotationPresent(RestController.class);
  }

  /**
   * Registers all mapping methods of a controller bean with the request handler.
   *
   * <p>This method examines all public methods of a controller bean and processes those that have
   * Spring MVC mapping annotations. It handles the following mapping annotations:
   *
   * <ul>
   *   <li>{@link RequestMapping}
   *   <li>{@link PostMapping}
   *   <li>{@link GetMapping}
   *   <li>{@link PutMapping}
   *   <li>{@link DeleteMapping}
   * </ul>
   *
   * <p>Each method's mapping is combined with the class-level root path to create the complete
   * request mapping path.
   *
   * @param bean the controller bean instance, must not be null
   * @param beanName the name of the bean in the Spring context, must not be null
   * @param classRootPath the root path from class-level {@link RequestMapping}, may be empty
   * @param reverseRegister if {@code true}, unregisters the mappings instead of registering them
   * @see #processMappingIfPresent(Object, Method, String, String, Class, boolean)
   */
  private void processControllerMethods(
      Object bean, String beanName, String classRootPath, boolean reverseRegister) {
    Class<?> clazz = bean.getClass();
    Arrays.stream(clazz.getMethods())
        .forEach(
            method -> {
              logger.info(
                  "Processing method {} of controller {} to main application's HandlerMapping",
                  method.getName(),
                  beanName);

              // Try each mapping annotation type
              if (processMappingIfPresent(
                  bean, method, beanName, classRootPath, RequestMapping.class, reverseRegister)) {
                return;
              }
              if (processMappingIfPresent(
                  bean, method, beanName, classRootPath, PostMapping.class, reverseRegister)) {
                return;
              }
              if (processMappingIfPresent(
                  bean, method, beanName, classRootPath, GetMapping.class, reverseRegister)) {
                return;
              }
              if (processMappingIfPresent(
                  bean, method, beanName, classRootPath, PutMapping.class, reverseRegister)) {
                return;
              }
              processMappingIfPresent(
                  bean, method, beanName, classRootPath, DeleteMapping.class, reverseRegister);
            });
  }

  /**
   * Registers a mapping if the specified annotation is present on the method.
   *
   * <p>This method checks if a method has a specific mapping annotation and, if present, creates
   * and registers the corresponding {@link RequestMappingInfo} with the main application's request
   * handler mapping.
   *
   * <p>The method handles path combination, HTTP method determination, and other mapping properties
   * based on the annotation type and values.
   *
   * @param bean the controller bean instance, must not be null
   * @param method the method to check for mapping annotations, must not be null
   * @param beanName the name of the bean in the Spring context, must not be null
   * @param classRootPath the root path from class-level mapping, may be empty
   * @param annotationType the type of mapping annotation to look for, must not be null
   * @param reverseRegister if {@code true}, unregisters the mapping instead of registering it
   * @return {@code true} if the annotation was found and processed, {@code false} otherwise
   * @see #processMappingInfo(Annotation, String, Class)
   */
  private boolean processMappingIfPresent(
      Object bean,
      Method method,
      String beanName,
      String classRootPath,
      Class<? extends Annotation> annotationType,
      boolean reverseRegister) {
    Annotation annotation = method.getAnnotation(annotationType);
    if (annotation == null) {
      return false;
    }

    RequestMappingInfo mappingInfo = processMappingInfo(annotation, classRootPath, annotationType);
    if (mappingInfo != null) {
      String mappingType = annotationType.getSimpleName().replace("Mapping", "").toLowerCase();
      String[] paths =
          Arrays.stream(getMappingPaths(annotation, annotationType))
              .map(path -> classRootPath + path)
              .map(path -> path.endsWith("/") ? path.substring(0, path.length() - 1) : path)
              .toArray(String[]::new);
      logger.info(
          "Processing {} mapping {} for controller {}",
          mappingType,
          Arrays.toString(paths),
          beanName);
      if (reverseRegister) {
        requestMappingHandlerMapping.unregisterMapping(mappingInfo);
        return true;
      }
      requestMappingHandlerMapping.registerMapping(mappingInfo, bean, method);
    }
    return true;
  }

  /**
   * Creates RequestMappingInfo based on the annotation type and its values.
   *
   * <p>This method constructs a {@link RequestMappingInfo} object from a mapping annotation,
   * extracting all relevant information including:
   *
   * <ul>
   *   <li>Request paths (combined with class root path)
   *   <li>HTTP methods based on annotation type
   *   <li>Request parameters, headers, consumes, and produces constraints
   *   <li>Mapping name for identification
   * </ul>
   *
   * <p>The method handles different annotation types by setting appropriate HTTP methods and
   * extracting type-specific properties.
   *
   * @param annotation the mapping annotation instance, must not be null
   * @param classRootPath the root path from class-level mapping, may be empty
   * @param annotationType the type of the mapping annotation, must not be null
   * @return a configured RequestMappingInfo, or null if processing fails
   * @see #addCommonMappingProperties(RequestMappingInfo.Builder, Annotation, Class)
   * @see #getMappingPaths(Annotation, Class)
   */
  private RequestMappingInfo processMappingInfo(
      Annotation annotation, String classRootPath, Class<? extends Annotation> annotationType) {
    try {
      RequestMappingInfo.Builder builder =
          RequestMappingInfo.paths(
              Arrays.stream(getMappingPaths(annotation, annotationType))
                  .map(path -> classRootPath + path)
                  .map(path -> path.endsWith("/") ? path.substring(0, path.length() - 1) : path)
                  .toArray(String[]::new));

      // Set HTTP methods based on annotation type
      if (annotationType == PostMapping.class) {
        builder.methods(RequestMethod.POST);
      } else if (annotationType == GetMapping.class) {
        builder.methods(RequestMethod.GET);
      } else if (annotationType == PutMapping.class) {
        builder.methods(RequestMethod.PUT);
      } else if (annotationType == DeleteMapping.class) {
        builder.methods(RequestMethod.DELETE);
      } else if (annotationType == RequestMapping.class) {
        RequestMapping requestMapping = (RequestMapping) annotation;
        builder.methods(requestMapping.method());
      }

      // Add common properties for all mapping types
      addCommonMappingProperties(builder, annotation, annotationType);

      return builder.build();
    } catch (Exception e) {
      logger.error(
          "Failed to process mapping info for annotation {}", annotationType.getSimpleName(), e);
      return null;
    }
  }

  /**
   * Adds common mapping properties (params, headers, consumes, produces, name) to the builder.
   *
   * <p>This method sets common properties from the mapping annotation, such as request parameters,
   * headers, content types, and mapping names. If a property is not defined in the annotation, it
   * is left unset in the builder.
   *
   * @param builder the RequestMappingInfo builder to modify, must not be null
   * @param annotation the mapping annotation instance, must not be null
   * @param annotationType the type of the mapping annotation, must not be null
   * @see #getMappingPaths(Annotation, Class)
   */
  private void addCommonMappingProperties(
      RequestMappingInfo.Builder builder,
      Annotation annotation,
      Class<? extends Annotation> annotationType) {
    try {
      Method paramsMethod = annotationType.getMethod("params");
      Method headersMethod = annotationType.getMethod("headers");
      Method consumesMethod = annotationType.getMethod("consumes");
      Method producesMethod = annotationType.getMethod("produces");
      Method nameMethod = annotationType.getMethod("name");

      builder
          .params((String[]) paramsMethod.invoke(annotation))
          .headers((String[]) headersMethod.invoke(annotation))
          .consumes((String[]) consumesMethod.invoke(annotation))
          .produces((String[]) producesMethod.invoke(annotation))
          .mappingName((String) nameMethod.invoke(annotation));
    } catch (Exception e) {
      // Some annotations might not have all properties, which is fine
      logger.debug(
          "Could not set all mapping properties for {}: {}",
          annotationType.getSimpleName(),
          e.getMessage());
    }
  }

  /**
   * Extracts the path values from a mapping annotation.
   *
   * <p>This method attempts to retrieve the path values defined in a mapping annotation, typically
   * annotated with {@code @RequestMapping}, {@code @GetMapping}, etc. The paths are expected to be
   * an array of strings.
   *
   * <p>If the annotation does not define a {@code value} method or if an error occurs during
   * invocation, an empty array is returned.
   *
   * @param annotation the mapping annotation instance, must not be null
   * @param annotationType the type of the mapping annotation, must not be null
   * @return an array of path strings, or an empty array if extraction fails
   */
  private String[] getMappingPaths(
      Annotation annotation, Class<? extends Annotation> annotationType) {
    try {
      Method valueMethod = annotationType.getMethod("value");
      return (String[]) valueMethod.invoke(annotation);
    } catch (Exception e) {
      logger.warn(
          "Could not extract paths from {}: {}", annotationType.getSimpleName(), e.getMessage());
      return new String[0];
    }
  }

  /**
   * Destroys the Spring context for a plugin and cleans up all associated resources.
   *
   * <p>This method shuts down the Spring context associated with the specified plugin, removing all
   * beans and closing the context. If the plugin context does not exist, the method logs a warning
   * and returns without performing any action.
   *
   * @param pluginId the unique identifier of the plugin whose context should be destroyed, must not
   *     be null
   */
  public void destroyPluginContext(String pluginId) {
    AnnotationConfigApplicationContext pluginContext = pluginContexts.remove(pluginId);

    if (pluginContext != null) {
      try {
        logger.info("Destroying Spring context for plugin: {}", pluginId);
        String[] beanNames = pluginContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
          Object bean = pluginContext.getBean(beanName);
          processController(beanName, bean, true);
          if (mainApplicationContext
              instanceof ConfigurableApplicationContext configurableMainContext) {
            if (configurableMainContext.containsBean(beanName)) {
              logger.info(
                  "Removing bean {} of plugin {} from the main context", beanName, pluginId);
              // Remove the bean from the main context
              configurableMainContext.getBeanFactory().destroyBean(beanName);
              configurableMainContext.getBeanFactory().getBeanDefinition(beanName);
              if (configurableMainContext.getBeanFactory()
                  instanceof BeanDefinitionRegistry registry) {
                registry.removeBeanDefinition(beanName);
              }
            }
          }
        }

        // Close the context
        pluginContext.close();

        logger.info("Successfully destroyed Spring context for plugin: {}", pluginId);

      } catch (Exception e) {
        logger.error("Failed to destroy Spring context for plugin: {}", pluginId, e);
      }
    }
  }

  /**
   * Retrieves the Spring context for a plugin.
   *
   * <p>This method returns the Spring context associated with the specified plugin ID. If the
   * plugin context does not exist, the method returns {@code null}.
   *
   * @param pluginId the unique identifier of the plugin whose context should be retrieved, must not
   *     be null
   * @return the Spring context for the specified plugin, or {@code null} if not found
   */
  public AnnotationConfigApplicationContext getPluginContext(String pluginId) {
    return pluginContexts.get(pluginId);
  }
}
