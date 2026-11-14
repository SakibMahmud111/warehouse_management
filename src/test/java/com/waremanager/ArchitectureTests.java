package com.waremanager;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the MVC structure required by the CSE470 outline.
 * Runs with `mvn test` and on GitHub Actions for every pull request.
 *
 * If a rule fails, move the code into the correct layer. Do not change or delete a rule
 * without agreement from the whole team.
 */
@AnalyzeClasses(packages = "com.waremanager", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    /**
     * Requests flow one way: Controller -> Service -> Repository.
     * Controllers never use repositories directly, and nothing calls a controller.
     */
    @ArchTest
    static final ArchRule mvcLayersAreRespected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Config")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

    /** Entities (the Model's data classes) must not depend on controllers, services, repositories or config. */
    @ArchTest
    static final ArchRule modelDoesNotDependOnOtherLayers = noClasses()
            .that().resideInAPackage("..model..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..", "..repository..", "..config..");

    /** Every *Controller class lives in the controller package and is a Spring MVC controller. */
    @ArchTest
    static final ArchRule controllersAreSpringMvcControllers = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..controller..")
            .andShould().beMetaAnnotatedWith(Controller.class);

    /** Controllers stay thin: database work (JPA, EntityManager) belongs in services and repositories. */
    @ArchTest
    static final ArchRule controllersDoNotUseJpaDirectly = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.springframework.data.jpa..");
}
