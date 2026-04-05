package com.code2code.service.converter;

/**
 * Interface for generating UI framework code from FormSpecification.
 * Implementations can generate React, Vue, Angular, etc.
 */
public interface ComponentGenerator {
    /**
     * Generates component code from a form specification.
     *
     * @param spec The form specification to convert
     * @param options Generation options (target framework, styling approach, etc.)
     * @return Generated component code
     * @throws GenerationException if generation fails
     */
    String generate(FormSpecification spec, GenerationOptions options) throws GenerationException;
    
    /**
     * Determines if this generator supports the given target framework.
     *
     * @param target The target framework name (e.g., "react", "vue")
     * @return true if this generator can produce code for the target
     */
    boolean supports(String target);
}
