package org.springframework.roo.addon.layers.repository.jpa.addon;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.roo.addon.layers.repository.jpa.annotations.RooJpaRepositoryCustom;
import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.itd.AbstractItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.DataType;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.SpringJavaType;
import org.springframework.roo.project.LogicalPath;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Metadata for {@link RooJpaRepositoryCustom}.
 * 
 * @author Paula Navarro
 * @since 2.0
 */
public class RepositoryJpaCustomMetadata extends AbstractItdTypeDetailsProvidingMetadataItem {

  private static final String PROVIDES_TYPE_STRING = RepositoryJpaCustomMetadata.class.getName();
  private static final String PROVIDES_TYPE = MetadataIdentificationUtils
      .create(PROVIDES_TYPE_STRING);

  private JavaType globalSearch;
  private JavaType defaultReturnType;
  private Map<FieldMetadata, MethodMetadata> referencedFieldsFindAllMethods;
  private List<MethodMetadata> projectionFinderMethods;

  public static String createIdentifier(final JavaType javaType, final LogicalPath path) {
    return PhysicalTypeIdentifierNamingUtils.createIdentifier(PROVIDES_TYPE_STRING, javaType, path);
  }

  public static JavaType getJavaType(final String metadataIdentificationString) {
    return PhysicalTypeIdentifierNamingUtils.getJavaType(PROVIDES_TYPE_STRING,
        metadataIdentificationString);
  }

  public static String getMetadataIdentiferType() {
    return PROVIDES_TYPE;
  }

  public static LogicalPath getPath(final String metadataIdentificationString) {
    return PhysicalTypeIdentifierNamingUtils.getPath(PROVIDES_TYPE_STRING,
        metadataIdentificationString);
  }

  public static boolean isValid(final String metadataIdentificationString) {
    return PhysicalTypeIdentifierNamingUtils.isValid(PROVIDES_TYPE_STRING,
        metadataIdentificationString);
  }

  /**
   * Constructor
   * 
   * @param identifier the identifier for this item of metadata (required)
   * @param aspectName the Java type of the ITD (required)
   * @param governorPhysicalTypeMetadata the governor, which is expected to
   *            contain a {@link ClassOrInterfaceTypeDetails} (required)
   * @param annotationValues (required)
   * @param identifierType the type of the entity's identifier field
   *            (required)
   * @param domainType entity referenced on interface
   * @param searchResult the java type o the search result returned by findAll finder
   * @param globalSearch the class annotated with @RooGlobalSearch 
   * @param referencedFields map that contains referenced field and its identifier field type
   * @param findersToAdd 
   */
  public RepositoryJpaCustomMetadata(final String identifier, final JavaType aspectName,
      final PhysicalTypeMetadata governorPhysicalTypeMetadata,
      final RepositoryJpaCustomAnnotationValues annotationValues, final JavaType domainType,
      final JavaType defaultReturnType, JavaType globalSearch,
      final Map<FieldMetadata, JavaType> referencedFields, List<ProjectionFinderMethod> findersToAdd) {
    super(identifier, aspectName, governorPhysicalTypeMetadata);
    Validate.notNull(annotationValues, "Annotation values required");
    Validate.notNull(globalSearch, "Global search required");
    Validate.notNull(defaultReturnType, "Search result required");
    Validate.notNull(referencedFields, "Referenced fields could be empty but not null");

    this.globalSearch = globalSearch;
    this.defaultReturnType = defaultReturnType;
    this.projectionFinderMethods = new ArrayList<MethodMetadata>();

    referencedFieldsFindAllMethods = new HashMap<FieldMetadata, MethodMetadata>();

    // Generate findAll method
    ensureGovernorHasMethod(new MethodMetadataBuilder(getFindAllGlobalSearchMethod()));

    // ROO-3765: Prevent ITD regeneration applying the same sort to provided map. If this sort is not applied, maybe some
    // method is not in the same order and ITD will be regenerated.
    Map<FieldMetadata, JavaType> referencedFieldsOrderedByFieldName =
        new TreeMap<FieldMetadata, JavaType>(new Comparator<FieldMetadata>() {
          @Override
          public int compare(FieldMetadata field1, FieldMetadata field2) {
            return field1.getFieldName().compareTo(field2.getFieldName());
          }
        });
    referencedFieldsOrderedByFieldName.putAll(referencedFields);

    // Generate findAllMethod for every referencedFields
    for (Entry<FieldMetadata, JavaType> referencedField : referencedFieldsOrderedByFieldName
        .entrySet()) {
      MethodMetadata method =
          getFindAllMethodByReferencedField(referencedField.getKey(), referencedField.getValue());
      ensureGovernorHasMethod(new MethodMetadataBuilder(method));
      referencedFieldsFindAllMethods.put(referencedField.getKey(), method);
    }

    // Generate finder methods if any
    for (ProjectionFinderMethod finderMethod : findersToAdd) {
      MethodMetadata method =
          getProjectionFinder(finderMethod.getReturnType(), finderMethod.getMethodName(),
              finderMethod.getFormBean());
      ensureGovernorHasMethod(new MethodMetadataBuilder(method));
      if (!projectionFinderMethods.contains(method)) {
        projectionFinderMethods.add(method);
      }
    }


    // Build the ITD
    itdTypeDetails = builder.build();
  }

  /**
   * Method that generates the findAll method on current interface. 
   * 
   * @return
   */
  public MethodMetadata getFindAllGlobalSearchMethod() {

    // Define method parameter types and parameter names
    List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
    List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();

    //Global search parameter
    parameterTypes.add(new AnnotatedJavaType(globalSearch));
    parameterNames.add(new JavaSymbolName("globalSearch"));

    // Pageable parameter
    parameterTypes.add(new AnnotatedJavaType(new JavaType(
        "org.springframework.data.domain.Pageable")));
    parameterNames.add(new JavaSymbolName("pageable"));

    // Method name
    JavaSymbolName methodName = new JavaSymbolName("findAll");

    // Return type
    JavaType returnType =
        new JavaType("org.springframework.data.domain.Page", 0, DataType.TYPE, null,
            Arrays.asList(defaultReturnType));

    // Use the MethodMetadataBuilder for easy creation of MethodMetadata
    MethodMetadataBuilder methodBuilder =
        new MethodMetadataBuilder(getId(), Modifier.PUBLIC + Modifier.ABSTRACT, methodName,
            returnType, parameterTypes, parameterNames, null);

    return methodBuilder.build(); // Build and return a MethodMetadata
  }

  /**
   * Method that generates the findAll method for provided referenced field on current interface. 
   * 
   * @param referencedField
   * @param identifierType
   * 
   * @return
   */
  public MethodMetadata getFindAllMethodByReferencedField(FieldMetadata referencedField,
      JavaType identifierType) {

    // Method name
    JavaSymbolName methodName =
        new JavaSymbolName(String.format("findAllBy%s",
            StringUtils.capitalize(referencedField.getFieldName().getSymbolName())));

    // Define method parameter types and parameter names
    List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
    parameterTypes.add(AnnotatedJavaType.convertFromJavaType(referencedField.getFieldType()));
    parameterTypes.add(AnnotatedJavaType.convertFromJavaType(globalSearch));
    parameterTypes.add(new AnnotatedJavaType(SpringJavaType.PAGEABLE));

    List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
    parameterNames.add(referencedField.getFieldName());
    parameterNames.add(new JavaSymbolName("globalSearch"));
    parameterNames.add(new JavaSymbolName("pageable"));

    // Return type
    JavaType returnType =
        new JavaType(SpringJavaType.PAGE.getFullyQualifiedTypeName(), 0, DataType.TYPE, null,
            Arrays.asList(defaultReturnType));

    // Use the MethodMetadataBuilder for easy creation of MethodMetadata
    MethodMetadataBuilder methodBuilder =
        new MethodMetadataBuilder(getId(), Modifier.PUBLIC + Modifier.ABSTRACT, methodName,
            returnType, parameterTypes, parameterNames, null);

    return methodBuilder.build(); // Build and return a MethodMetadata
  }

  /**
   * Method that generates finder methods whose return types are projections.
   * 
   * @param finderReturnType 
   * @param finderName
   * @param parameterType
   *
   * @return
   */
  public MethodMetadata getProjectionFinder(JavaType finderReturnType, JavaSymbolName finderName,
      JavaType parameterType) {

    // Define method parameter types and parameter names
    List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
    parameterTypes.add(AnnotatedJavaType.convertFromJavaType(parameterType));
    parameterTypes.add(AnnotatedJavaType.convertFromJavaType(globalSearch));
    parameterTypes.add(new AnnotatedJavaType(SpringJavaType.PAGEABLE));

    List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
    parameterNames.add(new JavaSymbolName("formBean"));
    parameterNames.add(new JavaSymbolName("globalSearch"));
    parameterNames.add(new JavaSymbolName("pageable"));

    // Return type
    JavaType returnType =
        new JavaType(SpringJavaType.PAGE.getFullyQualifiedTypeName(), 0, DataType.TYPE, null,
            Arrays.asList(finderReturnType));

    // Use the MethodMetadataBuilder for easy creation of MethodMetadata
    MethodMetadataBuilder methodBuilder =
        new MethodMetadataBuilder(getId(), Modifier.PUBLIC + Modifier.ABSTRACT, finderName,
            returnType, parameterTypes, parameterNames, null);

    return methodBuilder.build(); // Build and return a MethodMetadata
  }

  @Override
  public String toString() {
    final ToStringBuilder builder = new ToStringBuilder(this);
    builder.append("identifier", getId());
    builder.append("valid", valid);
    builder.append("aspectName", aspectName);
    builder.append("destinationType", destination);
    builder.append("governor", governorPhysicalTypeMetadata.getId());
    builder.append("itdTypeDetails", itdTypeDetails);
    return builder.toString();
  }

  public JavaType getDefaultReturnType() {
    return defaultReturnType;
  }

  /**
   * This method returns all findAll methods for 
   * referenced fields
   * 
   * @return
   */
  public Map<FieldMetadata, MethodMetadata> getReferencedFieldsFindAllMethods() {
    return referencedFieldsFindAllMethods;
  }

  /**
   * This method returns all finder methods which return a projection 
   * 
   * @return
   */
  public List<MethodMetadata> getProjectionFinderMethods() {
    return projectionFinderMethods;
  }
}
