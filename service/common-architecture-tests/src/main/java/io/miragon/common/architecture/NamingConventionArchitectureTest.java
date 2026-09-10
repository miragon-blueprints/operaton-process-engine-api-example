package io.miragon.common.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static com.tngtech.archunit.lang.conditions.ArchConditions.haveSimpleNameEndingWith;

/**
 * Naming conventions per hexagonal layer — the ArchUnit part of the combined suite (a simple
 * bytecode name check, so ArchUnit owns it).
 *
 * <p>Each rule lists the class-name suffixes allowed in a given package with a short rationale via
 * {@link AllowedSuffix}, so the file doubles as living documentation. Packages a given service does
 * not have simply match no classes ({@code allowEmptyShould(true)}), so the same rule set fits
 * every service. The generated {@code adapter.process} package is intentionally left unchecked.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class NamingConventionArchitectureTest {

    private final JavaClasses productionClasses;

    protected NamingConventionArchitectureTest(String rootPackage) {
        this.productionClasses =
                new ClassFileImporter()
                        .withImportOption(new DoNotIncludeTests())
                        .importPackages(rootPackage);
    }

    @Nested
    public class Ports {

        @Test
        @DisplayName("inbound ports are use-cases or queries")
        void inboundPortsAreUseCasesOrQueries() {
            checkNaming(
                    "..application.port.inbound",
                    List.of(
                            new AllowedSuffix("UseCase", "inbound port for a state-changing operation"),
                            new AllowedSuffix("Query", "inbound port for a read-only operation")));
        }

        @Test
        @DisplayName("outbound ports are ports or repositories")
        void outboundPortsArePortsOrRepositories() {
            checkNaming(
                    "..application.port.outbound",
                    List.of(
                            new AllowedSuffix("Port", "generic outbound port delegating to infrastructure"),
                            new AllowedSuffix("Repository", "outbound port for persistence access"),
                            new AllowedSuffix("Process", "outbound port driving the process engine")));
        }
    }

    @Nested
    public class Application {

        @Test
        @DisplayName("application services are services")
        void applicationServicesAreServices() {
            checkNaming(
                    "..application.service",
                    List.of(
                            new AllowedSuffix("Service", "orchestrates a use case and owns the transaction boundary"),
                            new AllowedSuffix("Configuration", "Spring configuration for the application layer")));
        }
    }

    @Nested
    public class InboundAdapters {

        @Test
        @DisplayName("rest adapters follow naming conventions")
        void restAdaptersFollowNamingConventions() {
            checkNaming(
                    "..adapter.inbound.rest",
                    List.of(
                            new AllowedSuffix("Controller", "Spring MVC REST controller"),
                            new AllowedSuffix("Dto", "REST response type outside the domain model"),
                            new AllowedSuffix("Input", "REST request type"),
                            new AllowedSuffix("Mapper", "translates between REST DTOs and domain types"),
                            new AllowedSuffix(
                                    "Configuration",
                                    "Spring web configuration for the REST adapter (CORS, OpenAPI metadata, error handling)")));
        }

        @Test
        @DisplayName("operaton adapters follow naming conventions")
        void operatonAdaptersFollowNamingConventions() {
            checkNaming(
                    "..adapter.inbound.operaton",
                    List.of(
                            new AllowedSuffix("Delegate", "JavaDelegate invoked by a BPMN service task"),
                            new AllowedSuffix("Worker", "external-task worker subscribed to a BPMN topic")));
        }
    }

    @Nested
    public class OutboundAdapters {

        @Test
        @DisplayName("outbound adapters follow naming conventions")
        void outboundAdaptersFollowNamingConventions() {
            checkNaming(
                    "..adapter.outbound",
                    List.of(
                            new AllowedSuffix("PersistenceAdapter", "primary outbound adapter implementing out-ports"),
                            new AllowedSuffix("Adapter", "outbound adapter adapting to infrastructure"),
                            new AllowedSuffix("Mapper", "translates between infrastructure types and domain objects"),
                            new AllowedSuffix("Entity", "JPA entity mapped to a database table"),
                            new AllowedSuffix("Repository", "Spring Data repository backing a persistence adapter")));
        }
    }

    private void checkNaming(String packageRoot, List<AllowedSuffix> allowedSuffixes) {
        if (allowedSuffixes.isEmpty()) {
            throw new IllegalArgumentException("allowedSuffixes must not be empty");
        }
        ArchCondition<JavaClass> nameCondition =
                allowedSuffixes.stream()
                        .map(allowedSuffix -> haveSimpleNameEndingWith(allowedSuffix.suffix()))
                        .reduce((acc, next) -> acc.or(next))
                        .orElseThrow();

        ArchRuleDefinition
                .classes()
                .that()
                .resideInAPackage(packageRoot + "..")
                .and()
                .areTopLevelClasses()
                .and()
                .areNotAnonymousClasses()
                // Transitional filter for Kotlin file-facade classes; removed in the Maven cutover.
                .and()
                .haveSimpleNameNotEndingWith("Kt")
                .and()
                .haveSimpleNameNotEndingWith("_")
                .and()
                .haveSimpleNameNotContaining("$")
                .should(nameCondition)
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /**
     * An allowed class-name suffix together with a short rationale.
     * The {@code reason} documents <i>why</i> a suffix is allowed; it is not part of ArchUnit's
     * failure message.
     */
    public record AllowedSuffix(String suffix, String reason) {
    }
}
