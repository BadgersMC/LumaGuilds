package net.badgersmc.nexus.scanning

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import net.badgersmc.nexus.annotations.*
import net.badgersmc.nexus.config.ConfigFile
import net.badgersmc.nexus.core.BeanDefinition
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Scans the classpath for components annotated with @Component, @Service, or @Repository.
 * Uses ClassGraph for reliable classpath scanning across custom classloaders.
 */
class ComponentScanner {

    private val logger = LoggerFactory.getLogger(ComponentScanner::class.java)

    /**
     * Scan the classpath for annotated component classes within the given package.
     *
     * Discovers all concrete (non-abstract, non-interface) classes annotated with
     * @Component, @Service, or @Repository in [basePackage] and its sub-packages.
     *
     * @param basePackage Base package to scan (e.g. "net.badgersmc.hycore")
     * @param classLoader The classloader to scan (typically the plugin's classloader)
     * @return List of [BeanDefinition] for each discovered component
     */
    fun scan(basePackage: String, classLoader: ClassLoader): List<BeanDefinition> {
        logger.debug("Scanning classpath for components in package: {}", basePackage)

        val annotationNames = listOf(
            Component::class.java.name,
            Service::class.java.name,
            Repository::class.java.name
        )

        val definitions = ClassGraph()
            .acceptPackages(basePackage)
            .addClassLoader(classLoader)
            .enableAnnotationInfo()
            .scan()
            .use { scanResult ->
                val classInfoList = annotationNames.flatMap { annotationName ->
                    scanResult.getClassesWithAnnotation(annotationName)
                }.distinct()

                classInfoList.mapNotNull { classInfo ->
                    // Skip abstract classes and interfaces — they can't be instantiated
                    if (classInfo.isAbstract || classInfo.isInterface) {
                        logger.debug("Skipping non-concrete class: {}", classInfo.name)
                        return@mapNotNull null
                    }

                    try {
                        val klass = classInfo.loadClass().kotlin
                        val beanName = determineBeanName(
                            klass,
                            klass.findAnnotation<Component>(),
                            klass.findAnnotation<Service>(),
                            klass.findAnnotation<Repository>()
                        )
                        val scope = klass.findAnnotation<Scope>()?.value ?: ScopeType.SINGLETON

                        BeanDefinition(
                            name = beanName,
                            type = klass,
                            scope = scope,
                            factory = { throw IllegalStateException("Factory will be set by context") }
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to process class: ${classInfo.name}", e)
                        null
                    }
                }
            }

        logger.info("Classpath scan found {} components in package '{}'", definitions.size, basePackage)
        return definitions
    }

    /**
     * Scan the classpath for classes annotated with @ConfigFile within the given package.
     *
     * Returns raw KClass references (not BeanDefinitions) since config classes
     * are loaded via ConfigManager rather than created by BeanFactory.
     *
     * @param basePackage Base package to scan (e.g. "net.badgersmc.hycore")
     * @param classLoader The classloader to scan
     * @return List of KClass for each discovered @ConfigFile class
     */
    fun scanConfigFiles(basePackage: String, classLoader: ClassLoader): List<KClass<*>> {
        logger.debug("Scanning classpath for @ConfigFile classes in package: {}", basePackage)

        val configClasses = ClassGraph()
            .acceptPackages(basePackage)
            .addClassLoader(classLoader)
            .enableAnnotationInfo()
            .scan()
            .use { scanResult ->
                scanResult.getClassesWithAnnotation(ConfigFile::class.java.name)
                    .filter { !it.isAbstract && !it.isInterface }
                    .mapNotNull { classInfo ->
                        try {
                            classInfo.loadClass().kotlin
                        } catch (e: Exception) {
                            logger.warn("Failed to load @ConfigFile class: ${classInfo.name}", e)
                            null
                        }
                    }
            }

        logger.info("Classpath scan found {} @ConfigFile classes in package '{}'", configClasses.size, basePackage)
        return configClasses
    }

    private fun determineBeanName(
        klass: KClass<*>,
        component: Component?,
        service: Service?,
        repository: Repository?
    ): String {
        val explicitName = component?.value?.takeIf { it.isNotEmpty() }
            ?: service?.value?.takeIf { it.isNotEmpty() }
            ?: repository?.value?.takeIf { it.isNotEmpty() }

        return explicitName ?: klass.simpleName!!.replaceFirstChar { it.lowercase() }
    }
}
