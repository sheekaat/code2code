package com.code2code.service.converter;

/**
 * Interface for form parsers that extract structured specifications from form files.
 * Implementations can handle VB6 .frm files, C# .cs files, etc.
 */
public interface FormParser {
    /**
     * Determines if this parser can handle the given file type.
     *
     * @param fileName The name of the file to check
     * @return true if this parser supports the file type
     */
    boolean supports(String fileName);
    
    /**
     * Parses a form file into a structured FormSpecification.
     *
     * @param content The raw content of the form file
     * @param fileName The name of the form file
     * @return A FormSpecification containing the parsed form structure
     * @throws ParseException if parsing fails
     */
    FormSpecification parse(String content, String fileName) throws ParseException;
}
