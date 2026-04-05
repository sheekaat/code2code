package com.code2code.service.converter;

import java.util.List;
import java.util.Map;

/**
 * Immutable data class representing a parsed form specification.
 * This is the intermediate representation between source forms (VB6, etc.) and target UI frameworks (React, etc.).
 */
public record FormSpecification(
    String formName,
    String caption,
    int clientWidth,
    int clientHeight,
    int startPosition,
    boolean controlBox,
    List<ControlDefinition> controls,
    Map<String, EventHandler> eventHandlers,
    List<Property> formProperties
) {
    public FormSpecification {
        controls = List.copyOf(controls != null ? controls : List.of());
        eventHandlers = Map.copyOf(eventHandlers != null ? eventHandlers : Map.of());
        formProperties = List.copyOf(formProperties != null ? formProperties : List.of());
    }
    
    /**
     * Represents a control (button, textbox, label, etc.) in the form.
     */
    public record ControlDefinition(
        String type,
        String name,
        int left,
        int top,
        int width,
        int height,
        String caption,
        boolean visible,
        boolean enabled,
        String tabIndex,
        FontProperties font,
        List<Property> customProperties,
        List<EventHandler> events
    ) {
        public ControlDefinition {
            customProperties = List.copyOf(customProperties != null ? customProperties : List.of());
            events = List.copyOf(events != null ? events : List.of());
        }
    }
    
    /**
     * Represents font properties for controls.
     */
    public record FontProperties(
        String name,
        float size,
        boolean bold,
        boolean italic,
        boolean underline
    ) {}
    
    /**
     * Represents an event handler associated with a control or form.
     */
    public record EventHandler(
        String eventName,
        String handlerName,
        String controlName
    ) {}
    
    /**
     * Represents a generic property (name-value pair).
     */
    public record Property(
        String name,
        String value,
        PropertyType type
    ) {
        public enum PropertyType {
            STRING, NUMBER, BOOLEAN, COLOR, ENUM
        }
    }
}
