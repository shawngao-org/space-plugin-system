# Space Plugin System

A dynamic, Spring Boot-based plugin management system that enables runtime loading, unloading, and hot-reloading of plugins with complete isolation and Spring integration.

## 🚀 Features

- **Dynamic Plugin Management**: Load, unload, and reload plugins at runtime without restarting the application
- **Plugin Isolation**: Each plugin runs in its own ClassLoader for complete isolation
- **Spring Integration**: Full Spring Framework support with automatic bean registration and dependency injection
- **Hot Reloading**: Automatic detection and reloading of plugin changes via file system monitoring

## 🏗️ Architecture

The system consists of two main modules:

### plugin-api
Contains the core interfaces and annotations that plugins must implement:
- `Plugin` interface - Core plugin contract
- `@PluginInfo` annotation - Plugin metadata and configuration

### plugin-system
The main application providing the plugin management infrastructure:
- **PluginManager** - Core plugin lifecycle management
- **PluginClassLoader** - Isolated class loading for plugins
- **PluginSpringContextManager** - Spring context integration
- **PluginWatcherService** - File system monitoring for hot reload
- **PluginController** - REST API endpoints

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Gradle 7.0 or higher

### Running the System

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd space-plugin-system
   ```

2. **Build the project**
   ```bash
   ./gradlew build
   ```

3. **Run the application**
   ```bash
   ./gradlew :plugin-system:bootRun
   ```

4. **Access the application**
   - Application: http://localhost:8080
   - Health endpoint: http://localhost:8080/actuator/health
   - Plugin API: http://localhost:8080/api/plugins

### Creating the Plugins Directory

The system looks for plugins in the `./plugins` directory by default. Create it if it doesn't exist:

```bash
mkdir plugins
```

## 🔌 Plugin Development

### Creating a Plugin

1. **Add the plugin-api dependency** to your plugin project:
   ```gradle
   dependencies {
       compileOnly 'org.spaceframework.ps:space-plugin-api:1.0.0-SNAPSHOT'
   }
   ```

2. **Implement the Plugin interface**:
   ```java
   @PluginInfo(
       id = "example-plugin",
       name = "Example Plugin",
       version = "1.0.0",
       description = "A sample plugin demonstrating the plugin system",
       author = "Your Name",
       scanBasePackages = {"com.example.plugin"}
   )
   public class ExamplePlugin implements Plugin {
       
       @Override
       public void onLoad() {
           System.out.println("Example plugin loaded!");
           // Initialize your plugin here
       }
       
       @Override
       public void onUnload() {
           System.out.println("Example plugin unloaded!");
           // Clean up resources here
       }
       
       @Override
       public String getPluginId() {
           return "example-plugin";
       }
       
       @Override
       public String getPluginName() {
           return "Example Plugin";
       }
       
       @Override
       public String getVersion() {
           return "1.0.0";
       }
       
       @Override
       public String getDescription() {
           return "A sample plugin";
       }
       
       @Override
       public String getAuthor() {
           return "Your Name";
       }
   }
   ```

3. **Add Spring components** (optional):
   ```java
   @RestController
   @RequestMapping("/example")
   public class ExampleController {
       
       @GetMapping("/hello")
       public String hello() {
           return "Hello from Example Plugin!";
       }
   }
   ```

4. **Build your plugin**:
   ```bash
   ./gradlew build
   ```

5. **Deploy the plugin**:
   Copy the generated JAR file to the `plugins` directory of the running system.

### Plugin Lifecycle

1. **Loading**: Plugin JAR is detected and loaded
2. **Initialization**: `onLoad()` method is called
3. **Active**: Plugin is running and serving requests
4. **Unloading**: `onUnload()` method is called
5. **Cleanup**: Resources are released and plugin is removed

## 📚 API Reference

### Plugin Management Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/plugins` | List all loaded plugins |
| GET | `/api/plugins/{id}` | Get specific plugin details |
| POST | `/api/plugins/load` | Load a plugin from JAR path |
| POST | `/api/plugins/upload` | Upload and load a plugin JAR |
| DELETE | `/api/plugins/{id}` | Unload a plugin |
| PUT | `/api/plugins/{id}/reload` | Reload a plugin |
| GET | `/api/plugins/status` | Get system status |

### Example API Usage

**List all plugins:**
```bash
curl http://localhost:8080/api/plugins
```

**Load a plugin:**
```bash
curl -X POST http://localhost:8080/api/plugins/load \
  -H "Content-Type: application/json" \
  -d '{"jarPath": "/path/to/plugin.jar"}'
```

**Upload a plugin:**
```bash
curl -X POST http://localhost:8080/api/plugins/upload \
  -F "file=@plugin.jar"
```

## ⚙️ Configuration

### Application Configuration (application.yml)

```yaml
server:
  port: 8080

plugin:
  system:
    # Directory where plugins are stored
    plugins-directory: ./plugins
    # Enable hot reload functionality
    hot-reload-enabled: true
    # File watch interval in milliseconds
    watch-interval: 1000
    # Maximum number of plugins that can be loaded
    max-plugins: 100

management:
  endpoints:
    web:
      exposure:
        include: health,info,plugins
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `plugin.system.plugins-directory` | `./plugins` | Directory to scan for plugin JARs |
| `plugin.system.hot-reload-enabled` | `true` | Enable automatic plugin reloading |
| `plugin.system.watch-interval` | `1000` | File system watch interval (ms) |
| `plugin.system.max-plugins` | `100` | Maximum number of loaded plugins |

## 🔨 Building

### Build All Modules
```bash
./gradlew build
```

### Build Specific Module
```bash
./gradlew :plugin-api:build
./gradlew :plugin-system:build
```

### Run Tests
```bash
./gradlew test
```

### Generate Documentation
```bash
./gradlew javadoc
```

## 📖 Examples

### Simple Plugin Example

```java
@PluginInfo(
    id = "hello-world",
    name = "Hello World Plugin",
    version = "1.0.0",
    description = "Simple greeting plugin"
)
public class HelloWorldPlugin implements Plugin {
    
    @Override
    public void onLoad() {
        System.out.println("Hello World Plugin loaded!");
    }
    
    @Override
    public void onUnload() {
        System.out.println("Hello World Plugin unloaded!");
    }
    
    // ... implement other required methods
}
```

### Plugin with REST Controller

```java
@PluginInfo(
    id = "api-plugin",
    name = "API Plugin",
    version = "1.0.0",
    scanBasePackages = {"com.example.api"}
)
public class ApiPlugin implements Plugin {
    // Plugin implementation
}

@RestController
@RequestMapping("/api/example")
public class ExampleApiController {
    
    @GetMapping("/data")
    public Map<String, Object> getData() {
        return Map.of(
            "message", "Hello from plugin!",
            "timestamp", System.currentTimeMillis()
        );
    }
}
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🔧 Troubleshooting

### Common Issues

**Plugin not loading:**
- Ensure the JAR file is in the correct plugins directory
- Check that the plugin class implements the `Plugin` interface
- Verify the `@PluginInfo` annotation is present and valid

**ClassNotFoundException:**
- Check plugin dependencies are included in the JAR
- Ensure the plugin's main class is properly packaged

**Port conflicts:**
- Change the server port in `application.yml`
- Ensure no other applications are using port 8080

### Logging

Enable debug logging for detailed plugin system information:

```yaml
logging:
  level:
    org.spaceframework.ps: DEBUG
```

## 📞 Support

For questions, issues, or contributions, please:
- Open an issue on GitHub
- Check the documentation
- Review existing issues and discussions
