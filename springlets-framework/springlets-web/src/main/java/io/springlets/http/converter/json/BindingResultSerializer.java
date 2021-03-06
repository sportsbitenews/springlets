/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.springlets.http.converter.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson Serializer which generates a tree with binding and validation
 * property errors stored on a {@link BindingResult} object.
 * <p/>
 * Error messages will be {@link FieldError#getDefaultMessage()} so, it is
 * already translated to (current request) language (or supposed to).
 * <p/>
 * JSON generated for {@link List} binding errors:
 * 
 * <pre>
 *   { 
 *     OBJECT_INDEX : { FIELD1_NAME : FIELD_ERROR_MSG, FIELD2_NAME : FIELD_ERROR_MSG, ...}, 
 *     OBJECT_INDEX2 : { FIELD1_NAME : FIELD_ERROR_MSG, 
 *         FIELD_OBJECT_NAME : { SUBOBJECT_FIELD: FIELD_ERROR_MSG, ... }
 *         FIELD_LIST_NAME: {
 *              OBJECT_FIELD_ITEM_INDEX : {ITEM_LIST_FIELD: FIELD_ERROR_MSG, ... },
 *              OBJECT_FIELD_ITEM_INDEX2 : {ITEM_LIST_FIELD: FIELD_ERROR_MSG, ... },
 *         },
 *         ...
 *     },
 *     ... 
 *   }
 * </pre>
 * 
 * JSON for object binding errors:
 * 
 * <pre>
 * { FIELD1_NAME : FIELD_ERROR_MSG, 
 *      FIELD_OBJECT_NAME : { SUBOBJECT_FIELD: FIELD_ERROR_MSG, ... }
 *      FIELD_LIST_NAME: {
 *              OBJECT_FIELD_ITEM_INDEX : {ITEM_LIST_FIELD: FIELD_ERROR_MSG, ... },
 *              OBJECT_FIELD_ITEM_INDEX2 : {ITEM_LIST_FIELD: FIELD_ERROR_MSG, ... },
 *      },
 *      ...
 * }
 * </pre>
 * @author http://www.disid.com[DISID Corporation S.L.]
 */
public class BindingResultSerializer extends JsonSerializer<BindingResult> {

  @Override
  public void serialize(BindingResult result, JsonGenerator jgen, SerializerProvider provider)
      throws IOException, JsonProcessingException {

    // Create the errors map
    Map<String, Object> allErrorsMessages = new HashMap<String, Object>();

    // Get field errors
    List<FieldError> fieldErrors = result.getFieldErrors();
    if (fieldErrors.isEmpty()) {
      // Nothing to do
      jgen.writeNull();
      return;
    }

    // Check if target type is an array or a bean
    @SuppressWarnings("rawtypes")
    Class targetClass = result.getTarget().getClass();
    if (targetClass.isArray() || Collection.class.isAssignableFrom(targetClass)) {
      loadListErrors(result.getFieldErrors(), allErrorsMessages);
    } else {
      loadObjectErrors(result.getFieldErrors(), allErrorsMessages);
    }

    // Create the result map
    Map<String, Object> bindingResult = new HashMap<String, Object>();
    bindingResult.put("target-resource", StringUtils.uncapitalize(result.getObjectName()));
    bindingResult.put("error-count", result.getErrorCount());
    bindingResult.put("errors", allErrorsMessages);

    jgen.writeObject(bindingResult);
  }

  /**
   * Iterate over object errors and load it on allErrorsMessages map.
   * <p/>
   * Delegates on {@link #loadObjectError(FieldError, String, Map)}
   * 
   * @param fieldErrors
   * @param allErrorsMessages
   */
  private void loadObjectErrors(List<FieldError> fieldErrors,
      Map<String, Object> allErrorsMessages) {

    for (FieldError error : fieldErrors) {
      loadObjectError(error, error.getField(), allErrorsMessages);
    }

  }

  /**
   * Iterate over list items errors and load it on allErrorsMessages map.
   * <p/>
   * Delegates on {@link #loadObjectError(FieldError, String, Map)}
   * 
   * @param fieldErrors
   * @param allErrorsMessages
   */
  @SuppressWarnings("unchecked")
  private void loadListErrors(List<FieldError> fieldErrors, Map<String, Object> allErrorsMessages) {

    // Get prefix to unwrapping list:
    // "list[0].employedSince"
    String fieldNamePath = fieldErrors.get(0).getField();
    String prefix = "";

    if (fieldNamePath != null && fieldNamePath.contains("[")) {
      // "list"
      prefix = substringBefore(fieldNamePath, "[");
    }

    String index = "";
    Map<String, Object> currentErrors;

    // Iterate over errors
    for (FieldError error : fieldErrors) {

      fieldNamePath = error.getField();

      if (fieldNamePath != null && fieldNamePath.contains("]")) {

        // get property path without list prefix
        // "[0].employedSince"
        fieldNamePath = substringAfter(error.getField(), prefix);

        // Get item's index:
        // "[0].employedSince"
        index = substringBefore(substringAfter(fieldNamePath, "["), "]");

        // Remove index definition from field path
        // "employedSince"
        fieldNamePath = substringAfter(fieldNamePath, ".");
      }

      // Check if this item already has errors registered
      currentErrors = (Map<String, Object>) allErrorsMessages.get(index);
      if (currentErrors == null) {
        // No errors registered: create map to contain this error
        currentErrors = new HashMap<String, Object>();
        allErrorsMessages.put(index, currentErrors);
      }

      // Load error on item's map
      loadObjectError(error, fieldNamePath, currentErrors);
    }
  }

  /**
   * Loads an object field error in errors map.
   * <p/>
   * This method identifies if referred object property is an array, an object
   * or a simple property to decide how to store the error message.
   * 
   * @param error
   * @param fieldNamePath
   * @param objectErrors
   */
  @SuppressWarnings("unchecked")
  private void loadObjectError(FieldError error, String fieldNamePath,
      Map<String, Object> objectErrors) {

    String propertyName = fieldNamePath;
    boolean isObject = false;

    // Get this property name and if is a object property
    if (fieldNamePath != null && fieldNamePath.contains(".")) {
      isObject = true;
      propertyName = substringBefore(fieldNamePath, ".");
    }

    // Check if property is an array or a list
    boolean isList = propertyName != null && propertyName.contains("[");

    // Process a list item property
    if (isList) {
      // Get property name
      String listPropertyName = substringBefore(propertyName, "[");

      // Get referred item index
      String index = substringBefore(substringAfter(propertyName, "["), "]");

      // Get item path
      String itemPath = substringAfter(fieldNamePath, ".");

      // Get container of list property errors
      Map<String, Object> listErrors = (Map<String, Object>) objectErrors.get(listPropertyName);

      if (listErrors == null) {
        // property has no errors yet: create a container for it
        listErrors = new HashMap<String, Object>();
        objectErrors.put(listPropertyName, listErrors);
      }

      // Get current item errors
      Map<String, Object> itemErrors = (Map<String, Object>) listErrors.get(index);

      if (itemErrors == null) {
        // item has no errors yet: create a container for it
        itemErrors = new HashMap<String, Object>();
        listErrors.put(index, itemErrors);
      }

      // Load error in item property path
      loadObjectError(error, itemPath, itemErrors);

    } else if (isObject) {
      // It's not a list but it has properties in it value

      // Get current property errors
      Map<String, Object> propertyErrors = (Map<String, Object>) objectErrors.get(propertyName);

      if (propertyErrors == null) {
        // item has no errors yet: create a container for it
        propertyErrors = new HashMap<String, Object>();
        objectErrors.put(propertyName, propertyErrors);
      }

      // Get error sub path
      String subFieldPath = substringAfter(fieldNamePath, ".");

      // Load error in container
      loadObjectError(error, subFieldPath, propertyErrors);

    } else {
      // standard property with no children value

      // Store error message in container
      objectErrors.put(propertyName, error.getDefaultMessage());
    }
  }

  private String substringBefore(String text, String separator) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }
    final int pos = text.indexOf(separator);
    if (pos < 0) {
      return text;
    }
    return text.substring(0, pos);
  }

  private String substringAfter(String text, final String separator) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }
    int pos = text.indexOf(separator);
    if (pos < 0) {
      return "";
    }
    return text.substring(pos + separator.length());
  }

}
