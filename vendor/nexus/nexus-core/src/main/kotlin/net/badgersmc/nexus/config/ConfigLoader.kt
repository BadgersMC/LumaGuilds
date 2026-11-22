package net.badgersmc.nexus.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Loads and saves configuration files using YAML format with annotation support.
 */
class ConfigLoader(private val configDirectory: Path) {

    private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)

    private val yaml = Yaml(configuration = YamlConfiguration(
        strictMode = false,
        encodeDefaults = true
    ))

    companion object {
        private const val CONFIG_FILE_EXTENSION = ".yaml"
    }

    init {
        if (!configDirectory.toFile().exists()) {
            configDirectory.toFile().mkdirs()
        }
    }

    /**
     * Load a configuration object from file.
     * If the file doesn't exist, creates it with default values.
     */
    fun <T : Any> load(configClass: KClass<T>): T {
        val instance = configClass.constructors.first().callBy(emptyMap())
        val fileName = getFileName(configClass)
        val file = configDirectory.resolve("$fileName$CONFIG_FILE_EXTENSION")

        if (!file.toFile().exists()) {
            // Create with defaults
            save(instance)
            return instance
        }

        try {
            val content = file.toFile().readText()
            val yamlNode = yaml.parseToYamlNode(content)
            val yamlMap = yamlNodeToMap(yamlNode)
            loadFromMap(instance, yamlMap)
        } catch (e: Exception) {
            logger.error("Failed to load config from $file: ${e.message}", e)
        }

        return instance
    }

    /**
     * Save a configuration object to file.
     */
    fun <T : Any> save(config: T) {
        val fileName = getFileName(config::class)
        val file = configDirectory.resolve("$fileName$CONFIG_FILE_EXTENSION")

        try {
            val yamlMap = saveToMap(config)
            val content = buildYamlString(config, yamlMap)
            file.toFile().writeText(content)
        } catch (e: Exception) {
            logger.error("Failed to save config to $file: ${e.message}", e)
        }
    }

    /**
     * Reload a configuration object from file.
     */
    fun <T : Any> reload(config: T) {
        val fileName = getFileName(config::class)
        val file = configDirectory.resolve("$fileName$CONFIG_FILE_EXTENSION")

        if (!file.toFile().exists()) {
            return
        }

        try {
            val content = file.toFile().readText()
            val yamlNode = yaml.parseToYamlNode(content)
            val yamlMap = yamlNodeToMap(yamlNode)
            loadFromMap(config, yamlMap)
        } catch (e: Exception) {
            logger.error("Failed to reload config from $file: ${e.message}", e)
        }
    }

    /**
     * Load values from map into object.
     */
    private fun <T : Any> loadFromMap(obj: T, map: Map<String, Any?>) {
        obj::class.memberProperties.forEach { property ->
            if (property !is KMutableProperty<*>) return@forEach
            if (property.findAnnotation<Transient>() != null) return@forEach

            val configName = property.findAnnotation<ConfigName>()?.value ?: property.name
            val value = map[configName] ?: return@forEach

            try {
                property.isAccessible = true
                val convertedValue = convertValue(property, value, obj)
                if (convertedValue != null) {
                    property.setter.call(obj, convertedValue)
                }
            } catch (e: Exception) {
                logger.error("Failed to load config field ${property.name}: ${e.message}")
            }
        }
    }

    /**
     * Convert a raw map [value] into the type expected by [property].
     * Nested objects are populated recursively via [loadFromMap].
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertValue(property: KProperty<*>, value: Any, obj: Any): Any? {
        return when (property.returnType.classifier) {
            String::class -> value.toString()
            Int::class -> (value as? Number)?.toInt()
            Long::class -> (value as? Number)?.toLong()
            Double::class -> (value as? Number)?.toDouble()
            Float::class -> (value as? Number)?.toFloat()
            Boolean::class -> value as? Boolean
            List::class -> value as? List<*>
            Set::class -> (value as? List<*>)?.toSet()
            Map::class -> value as? Map<*, *>
            else -> {
                // Nested object
                val nestedInstance = property.getter.call(obj)
                if (nestedInstance != null && value is Map<*, *>) {
                    loadFromMap(nestedInstance, value as Map<String, Any?>)
                    nestedInstance
                } else {
                    null
                }
            }
        }
    }

    /**
     * Save object values to map.
     */
    private fun <T : Any> saveToMap(obj: T): MutableMap<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        obj::class.memberProperties.forEach { property ->
            if (property.findAnnotation<Transient>() != null) return@forEach

            val configName = property.findAnnotation<ConfigName>()?.value ?: property.name

            try {
                property.isAccessible = true
                val value = property.getter.call(obj)

                when (value) {
                    null -> return@forEach
                    is String, is Number, is Boolean -> map[configName] = value
                    is List<*> -> map[configName] = value.map { serializeValue(it) }
                    is Set<*> -> map[configName] = value.map { serializeValue(it) }
                    is Map<*, *> -> map[configName] = value.entries.associate { (k, v) ->
                        k.toString() to serializeValue(v)
                    }
                    else -> {
                        // Nested object
                        map[configName] = saveToMap(value)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to save config field ${property.name}: ${e.message}")
            }
        }

        return map
    }

    /**
     * Recursively serialize a value for map storage.
     * Primitives pass through; custom objects are converted to maps via saveToMap().
     */
    private fun serializeValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is String, is Number, is Boolean -> value
            is List<*> -> value.map { serializeValue(it) }
            is Set<*> -> value.map { serializeValue(it) }
            is Map<*, *> -> value.entries.associate { (k, v) ->
                k.toString() to serializeValue(v)
            }
            else -> saveToMap(value)
        }
    }

    /**
     * Build YAML string with comments.
     */
    private fun <T : Any> buildYamlString(obj: T, map: Map<String, Any?>): String {
        val builder = StringBuilder()

        // Add class-level comment
        obj::class.findAnnotation<Comment>()?.let { comment ->
            comment.value.forEach { line ->
                builder.append("# $line\n")
            }
            builder.append("\n")
        }

        // Add properties with comments
        obj::class.memberProperties.forEach { property ->
            if (property.findAnnotation<Transient>() != null) return@forEach

            val configName = property.findAnnotation<ConfigName>()?.value ?: property.name
            val value = map[configName] ?: return@forEach

            // Add property comment
            property.findAnnotation<Comment>()?.let { comment ->
                comment.value.forEach { line ->
                    builder.append("# $line\n")
                }
            }

            // Add property value
            builder.append(formatYamlProperty(configName, value, 0))
            builder.append("\n")
        }

        return builder.toString()
    }

    /**
     * Format a property as YAML with proper indentation.
     */
    private fun formatYamlProperty(key: String, value: Any?, indent: Int): String {
        val indentStr = "  ".repeat(indent)
        return when (value) {
            null -> ""
            is String -> "$indentStr$key: \"$value\""
            is Number, is Boolean -> "$indentStr$key: $value"
            is List<*> -> {
                if (value.isEmpty()) {
                    "$indentStr$key: []"
                } else if (value.all { it is String || it is Number || it is Boolean }) {
                    // Simple list — inline items
                    val items = value.joinToString("\n") { item ->
                        "${indentStr}  - ${formatScalar(item)}"
                    }
                    "$indentStr$key:\n$items"
                } else {
                    // List of objects — each item is a map
                    val items = value.joinToString("\n") { item ->
                        formatListItem(item, indent + 1)
                    }
                    "$indentStr$key:\n$items"
                }
            }
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    "$indentStr$key: {}"
                } else {
                    val entries = value.entries.joinToString("\n") { (k, v) ->
                        formatYamlProperty(k.toString(), v, indent + 1)
                    }
                    "$indentStr$key:\n$entries"
                }
            }
            else -> "$indentStr$key: $value"
        }
    }

    /**
     * Format a list item that may be a scalar or an object (map).
     */
    private fun formatListItem(value: Any?, indent: Int): String {
        val indentStr = "  ".repeat(indent)
        return when (value) {
            null -> "${indentStr}- null"
            is String -> "${indentStr}- \"$value\""
            is Number, is Boolean -> "${indentStr}- $value"
            is Map<*, *> -> {
                val entries = value.entries.toList()
                if (entries.isEmpty()) {
                    "${indentStr}- {}"
                } else {
                    // First entry on the "- " line, rest indented
                    val first = entries.first()
                    val firstLine = "${indentStr}- ${first.key}: ${formatScalar(first.value)}"
                    if (entries.size == 1) {
                        firstLine
                    } else {
                        val rest = entries.drop(1).joinToString("\n") { (k, v) ->
                            formatYamlProperty(k.toString(), v, indent + 1)
                        }
                        "$firstLine\n$rest"
                    }
                }
            }
            else -> "${indentStr}- $value"
        }
    }

    /**
     * Format a scalar value for inline YAML output.
     */
    private fun formatScalar(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"$value\""
            is Number, is Boolean -> value.toString()
            else -> "\"$value\""
        }
    }

    /**
     * Convert a YamlNode tree into a Map<String, Any?> for reflection-based loading.
     */
    @Suppress("UNCHECKED_CAST")
    private fun yamlNodeToMap(node: YamlNode): Map<String, Any?> {
        val unwrapped = if (node is YamlTaggedNode) node.innerNode else node
        return when (unwrapped) {
            is YamlMap -> unwrapped.entries.map { (key, value) ->
                key.content to yamlNodeToAny(value)
            }.toMap()
            else -> emptyMap()
        }
    }

    private fun yamlNodeToAny(node: YamlNode): Any? {
        val unwrapped = if (node is YamlTaggedNode) node.innerNode else node
        return when (unwrapped) {
            is YamlNull -> null
            is YamlScalar -> {
                val content = unwrapped.content
                // Try to parse as number or boolean, fall back to string
                content.toBooleanStrictOrNull()
                    ?: content.toIntOrNull()
                    ?: content.toLongOrNull()
                    ?: content.toDoubleOrNull()
                    ?: content
            }
            is YamlMap -> unwrapped.entries.map { (key, value) ->
                key.content to yamlNodeToAny(value)
            }.toMap()
            is YamlList -> unwrapped.items.map { yamlNodeToAny(it) }
            else -> unwrapped.toString()
        }
    }

    /**
     * Get the config filename from @ConfigFile annotation or class name.
     */
    private fun getFileName(klass: KClass<*>): String {
        return klass.findAnnotation<ConfigFile>()?.value
            ?: klass.simpleName?.removeSuffix("Config")?.lowercase()
            ?: "config"
    }
}
