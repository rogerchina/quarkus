package io.quarkus.qute;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Represents a central point for template management.
 * <p>
 * It has a dedicated configuration and is able to cache the template definitions.
 *
 * @see EngineBuilder
 */
public interface Engine {

    /**
     *
     * @return a new builder instance
     */
    static EngineBuilder builder() {
        return new EngineBuilder();
    }

    /**
     * Parse the template contents.
     * <p>
     * Note that this method always returns a new {@link Template} instance.
     *
     * @param content
     * @return the template
     */
    default Template parse(String content) {
        return parse(content, null, null);
    }

    /**
     * Parse the template contents with the specified variant.
     * <p>
     * Note that this method always returns a new {@link Template} instance.
     *
     * @param content
     * @param variant
     * @return the template
     */
    default Template parse(String content, Variant variant) {
        return parse(content, variant, null);
    }

    /**
     * Parse the template contents with the specified variant and id.
     * <p>
     * Note that this method always returns a new {@link Template} instance.
     *
     * @param content
     * @param variant
     * @param id
     * @return the template
     */
    public Template parse(String content, Variant variant, String id);

    /**
     *
     * @return an immutable list of result mappers
     */
    public List<ResultMapper> getResultMappers();

    /**
     * Maps the given result to a string value. If no result mappers are available the {@link Object#toString()} value is used.
     *
     * @param result Must not be null
     * @param expression Must not be null
     * @return the string value
     * @see Engine#getResultMappers()
     */
    public String mapResult(Object result, Expression expression);

    /**
     *
     * @param id
     * @param template
     * @return the previous value or null
     */
    public Template putTemplate(String id, Template template);

    /**
     * Obtain a template for the given identifier. A template may be registered using
     * {@link #putTemplate(String, Template)} or loaded by a template locator.
     *
     * @param id
     * @return the template or null
     * @see EngineBuilder#addLocator(TemplateLocator)
     */
    public Template getTemplate(String id);

    /**
     * Note that template locators are not used in this method.
     *
     * @param id
     * @return {@code true} if a template with the given identifier is loaded, {@code false} otherwise
     */
    public boolean isTemplateLoaded(String id);

    /**
     * Removes all templates from the cache.
     */
    public void clearTemplates();

    /**
     * Removes the templates for which the mapping id matches the given predicate.
     *
     * @param test
     */
    public void removeTemplates(Predicate<String> test);

    /**
     *
     * @param name
     * @return the section helper factory for the giben name
     */
    public SectionHelperFactory<?> getSectionHelperFactory(String name);

    /**
     *
     * @return an immutable map of section helper factories
     */
    public Map<String, SectionHelperFactory<?>> getSectionHelperFactories();

    /**
     *
     * @return an immutable list of value resolvers
     */
    public List<ValueResolver> getValueResolvers();

    /**
     *
     * @return an immutable list of namespace resolvers
     */
    public List<NamespaceResolver> getNamespaceResolvers();

    /**
     *
     * @return the evaluator used to evaluate expressions
     */
    public Evaluator getEvaluator();

    /**
     * @return an immutable list of template instance initializers
     */
    public List<TemplateInstance.Initializer> getTemplateInstanceInitializers();

    /**
     * The global rendering timeout in milliseconds. It is used if no {@code timeout} instance attribute is set.
     *
     * @return the global rendering timeout
     * @see TemplateInstance#TIMEOUT
     */
    public long getTimeout();

    /**
     *
     * @return {@code true} if the timeout should also used for asynchronous rendering methods
     */
    public boolean useAsyncTimeout();

}
