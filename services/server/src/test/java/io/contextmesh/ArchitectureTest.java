package io.contextmesh;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.contextmesh", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule MODULES_ARE_ACYCLIC = slices()
            .matching("io.contextmesh.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule DOCUMENTED_DIRECTIONS = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Shared").definedBy("io.contextmesh.shared..")
            .layer("User").definedBy("io.contextmesh.user..")
            .layer("Conversation").definedBy("io.contextmesh.conversation..")
            .layer("Provider").definedBy("io.contextmesh.provider..")
            .layer("Ingestion").definedBy("io.contextmesh.ingestion..")
            .layer("Extraction").definedBy("io.contextmesh.extraction..")
            .layer("Provenance").definedBy("io.contextmesh.provenance..")
            .layer("Entity").definedBy("io.contextmesh.entity..")
            .layer("Graph").definedBy("io.contextmesh.graph..")
            .layer("Project").definedBy("io.contextmesh.project..")
            .layer("Retrieval").definedBy("io.contextmesh.retrieval..")
            .whereLayer("Shared").mayNotAccessAnyLayer()
            .whereLayer("User").mayOnlyAccessLayers("Shared")
            .whereLayer("Conversation").mayOnlyAccessLayers("User", "Provider", "Shared")
            .whereLayer("Provider").mayOnlyAccessLayers("User", "Shared")
            .whereLayer("Ingestion").mayOnlyAccessLayers("User", "Conversation", "Provider", "Shared")
            .whereLayer("Extraction").mayOnlyAccessLayers("Conversation", "Provider", "Shared")
            .whereLayer("Provenance").mayOnlyAccessLayers("Conversation", "Extraction", "Shared")
            .whereLayer("Entity").mayOnlyAccessLayers("Conversation", "Provenance", "Shared")
            .whereLayer("Graph").mayOnlyAccessLayers("Entity", "Provenance", "Shared")
            .whereLayer("Project").mayOnlyAccessLayers("Entity", "Conversation", "Provenance", "Shared")
            .whereLayer("Retrieval").mayOnlyAccessLayers("Conversation", "Entity", "Graph", "Project", "Provenance", "Provider", "Shared");
}
