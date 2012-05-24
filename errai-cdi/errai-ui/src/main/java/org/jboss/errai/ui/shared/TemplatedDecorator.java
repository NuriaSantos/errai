package org.jboss.errai.ui.shared;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.errai.codegen.InnerClass;
import org.jboss.errai.codegen.Parameter;
import org.jboss.errai.codegen.Statement;
import org.jboss.errai.codegen.builder.AnonymousClassStructureBuilder;
import org.jboss.errai.codegen.builder.BlockBuilder;
import org.jboss.errai.codegen.builder.ClassStructureBuilder;
import org.jboss.errai.codegen.builder.impl.ClassBuilder;
import org.jboss.errai.codegen.builder.impl.ObjectBuilder;
import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaClassFactory;
import org.jboss.errai.codegen.meta.MetaField;
import org.jboss.errai.codegen.meta.impl.build.BuildMetaClass;
import org.jboss.errai.codegen.util.PrivateAccessType;
import org.jboss.errai.codegen.util.Refs;
import org.jboss.errai.codegen.util.Stmt;
import org.jboss.errai.ioc.client.api.CodeDecorator;
import org.jboss.errai.ioc.client.container.InitializationCallback;
import org.jboss.errai.ioc.rebind.ioc.extension.IOCDecoratorExtension;
import org.jboss.errai.ioc.rebind.ioc.injector.InjectUtil;
import org.jboss.errai.ioc.rebind.ioc.injector.api.InjectableInstance;
import org.jboss.errai.ui.shared.api.annotations.Insert;
import org.jboss.errai.ui.shared.api.annotations.Replace;
import org.jboss.errai.ui.shared.api.annotations.Templated;

import com.google.common.base.Strings;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ClientBundle.Source;
import com.google.gwt.resources.client.TextResource;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
@CodeDecorator
public class TemplatedDecorator extends IOCDecoratorExtension<Templated> {
  private static final String CONSTRUCTED_TEMPLATE_SET_KEY = "constructedTemplate";

  public TemplatedDecorator(Class<Templated> decoratesWith) {
    super(decoratesWith);
  }

  @Override
  public List<? extends Statement> generateDecorator(InjectableInstance<Templated> ctx) {

    MetaClass declaringClass = ctx.getEnclosingType();

    for (MetaField field : declaringClass.getFields()) {
      if (field.isAnnotationPresent(Replace.class) || field.isAnnotationPresent(Insert.class)) {
        ctx.getInjectionContext().addExposedField(field, PrivateAccessType.Both);
      }
    }

    System.out.println("Handling @Templated annotation on field: " + declaringClass);

    MetaClass callbackMetaClass = MetaClassFactory.parameterizedAs(InitializationCallback.class,
            MetaClassFactory.typeParametersOf(declaringClass));
    BlockBuilder<AnonymousClassStructureBuilder> builder = ObjectBuilder.newInstanceOf(callbackMetaClass).extend()
            .publicOverridesMethod("init", Parameter.of(declaringClass, "obj"));

    /*
     * Do the work
     */
    generateTemplatedInitialization(ctx, builder);

    return Collections.singletonList(Stmt.loadVariable("context").invoke("addInitializationCallback",
            Refs.get(ctx.getInjector().getVarName()), builder.finish().finish()));
  }

  /**
   * Generate the actual construction logic for our {@link Templated} component
   */
  private void generateTemplatedInitialization(InjectableInstance<Templated> ctx,
          BlockBuilder<AnonymousClassStructureBuilder> builder) {

    Map<MetaClass, BuildMetaClass> constructed = getConstructedTemplateTypes(ctx);

    /*
     * Generate this component's ClientBundle if necessary
     */
    MetaClass declaringClass = ctx.getEnclosingType();
    if (!constructed.containsKey(declaringClass)) {
      generateTemplateResourceInterface(ctx, declaringClass);

      // replace current element of this component with HTML from template

      String templateVarName = InjectUtil.getUniqueVarName();
      builder.append(Stmt
              .declareVariable(getConstructedTemplateTypes(ctx).get(declaringClass))
              .named(templateVarName)
              .initializeWith(
                      Stmt.invokeStatic(GWT.class, "create", getConstructedTemplateTypes(ctx).get(declaringClass))));

      builder.append(Stmt.loadStatic(System.class, "out").invoke(
              "println",
              "Parsing template: " + getTemplateFileName(declaringClass) + "#"
                      + getTemplateFragmentName(declaringClass) + ""));

      String rootTemplateElementVarName = InjectUtil.getUniqueVarName();
      builder.append(Stmt
              .declareVariable(Element.class)
              .named(rootTemplateElementVarName)
              .initializeWith(
                      Stmt.invokeStatic(TemplateUtil.class, "getRootTemplateElement", Stmt
                              .loadVariable(templateVarName).invoke("getContents").invoke("getText"),
                              getTemplateFragmentName(declaringClass))));

      Statement rootTemplateElement = Stmt.loadVariable(rootTemplateElementVarName);
      Statement component = Refs.get(ctx.getInjector().getVarName());

      String dataFieldElementsVarName = InjectUtil.getUniqueVarName();
      builder.append(Stmt.declareVariable(Map.class).named(dataFieldElementsVarName)
              .initializeWith(Stmt.invokeStatic(TemplateUtil.class, "getDataFieldElements", rootTemplateElement)));

      Statement dataFieldElements = Stmt.loadVariable(dataFieldElementsVarName);

      builder.append(Stmt.invokeStatic(TemplateUtil.class, "initWidget", component, rootTemplateElement));

      generateComponentFieldCompositions(ctx, builder, component, dataFieldElements, declaringClass);
    }
  }

  private void generateComponentFieldCompositions(InjectableInstance<Templated> ctx,
          BlockBuilder<AnonymousClassStructureBuilder> builder, Statement component, Statement dataFieldElements,
          MetaClass declaringClass) {

    if (declaringClass.getSuperClass() != null)
      generateComponentFieldCompositions(ctx, builder, component, dataFieldElements, declaringClass.getSuperClass());

    for (MetaField field : declaringClass.getFields()) {
      if (field.isAnnotationPresent(Insert.class)) {
        builder.append(Stmt.invokeStatic(TemplateUtil.class, "compositeComponentInsert", InjectUtil
                .getPublicOrPrivateFieldValue(ctx.getInjectionContext().getProcessingContext(), component, field),
                dataFieldElements, getTemplateDataFieldName(field.getName(), field.getAnnotation(Insert.class))));
      }
      else if (field.isAnnotationPresent(Replace.class)) {
        builder.append(Stmt.invokeStatic(TemplateUtil.class, "compositeComponentReplace", InjectUtil
                .getPublicOrPrivateFieldValue(ctx.getInjectionContext().getProcessingContext(), component, field),
                dataFieldElements, getTemplateDataFieldName(field.getName(), field.getAnnotation(Replace.class))));
      }
    }
  }

  private String getTemplateDataFieldName(String fieldName, Insert anno) {
    String value = Strings.nullToEmpty(anno.value()).trim();
    return value.isEmpty() ? fieldName : value;
  }

  private String getTemplateDataFieldName(String fieldName, Replace anno) {
    String value = Strings.nullToEmpty(anno.value()).trim();
    return value.isEmpty() ? fieldName : value;
  }

  /**
   * Create an inner interface for the given {@link Template} class and its HTML
   * corresponding resource
   */
  private void generateTemplateResourceInterface(InjectableInstance<Templated> ctx, final MetaClass type) {
    ClassStructureBuilder<?> componentTemplateResource = ClassBuilder.define(getTemplateTypeName(type)).publicScope()
            .interfaceDefinition().implementsInterface(Template.class).implementsInterface(ClientBundle.class).body()
            .publicMethod(TextResource.class, "getContents").annotatedWith(new Source() {

              @Override
              public Class<? extends Annotation> annotationType() {
                return Source.class;
              }

              @Override
              public String[] value() {
                return new String[] { getTemplateFileName(type) };
              }

            }).finish();

    ctx.getInjectionContext().getProcessingContext().getBootstrapClass()
            .addInnerClass(new InnerClass(componentTemplateResource.getClassDefinition()));

    getConstructedTemplateTypes(ctx).put(type, componentTemplateResource.getClassDefinition());
  }

  /**
   * Get a map of all previously constructed {@link Template} object types
   */
  @SuppressWarnings("unchecked")
  private Map<MetaClass, BuildMetaClass> getConstructedTemplateTypes(InjectableInstance<Templated> ctx) {
    Map<MetaClass, BuildMetaClass> result = (Map<MetaClass, BuildMetaClass>) ctx.getInjectionContext().getAttribute(
            CONSTRUCTED_TEMPLATE_SET_KEY);

    if (result == null) {
      result = new HashMap<MetaClass, BuildMetaClass>();
      ctx.getInjectionContext().setAttribute(CONSTRUCTED_TEMPLATE_SET_KEY, result);
    }

    return result;
  }

  /*
   * Non-generation utility methods.
   */

  /**
   * Get the name of the {@link Template} class of the given {@link MetaClass}
   * type
   */
  private String getTemplateTypeName(MetaClass type) {
    return type.getFullyQualifiedName().replaceAll("\\.", "_") + "TemplateResource";
  }

  /**
   * Get the name of the {@link Template} HTML file of the given
   * {@link MetaClass} component type
   */
  private String getTemplateFileName(MetaClass type) {
    String resource = type.getFullyQualifiedName().replaceAll("\\.", "/") + ".html";

    if (type.isAnnotationPresent(Templated.class)) {
      String source = canonicalizeTemplateSourceSyntax(type, type.getAnnotation(Templated.class).value());
      Matcher matcher = Pattern.compile("^([^#]+)#?.*$").matcher(source);
      if (matcher.matches()) {
        resource = (matcher.group(1) == null ? resource : matcher.group(1));
        if (resource.matches("\\S+\\.html")) {
          resource = type.getPackageName().replaceAll("\\.", "/") + "/" + resource;
        }
      }
    }

    return resource;
  }

  /**
   * Get the name of the {@link Template} HTML fragment (Element subtree) to be
   * used as the template root of the given {@link MetaClass} component type
   */
  private String getTemplateFragmentName(MetaClass type) {
    String fragment = "";

    if (type.isAnnotationPresent(Templated.class)) {
      String source = canonicalizeTemplateSourceSyntax(type, type.getAnnotation(Templated.class).value());
      Matcher matcher = Pattern.compile("^.*#([^#]+)$").matcher(source);
      if (matcher.matches()) {
        fragment = (matcher.group(1) == null ? fragment : matcher.group(1));
      }
    }

    return fragment;
  }

  /**
   * Throw an exception if the template source syntax is invalid
   */
  private String canonicalizeTemplateSourceSyntax(MetaClass component, String source) {
    String result = Strings.nullToEmpty(source).trim();

    if (result.matches(".*#.*#.*")) {
      throw new IllegalArgumentException("Invalid syntax: @" + Templated.class.getSimpleName() + "(" + source
              + ") on component " + component.getFullyQualifiedName()
              + ". Multiple '#' found, where only one fragment is permitted.");
    }

    return result;
  }

}