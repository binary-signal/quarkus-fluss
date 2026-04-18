package io.quarkiverse.fluss.deployment;

import io.quarkiverse.fluss.FlussConnector;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

class FlussProcessor {

    private static final String FEATURE = "fluss";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerFlussConnector() {
        return AdditionalBeanBuildItem.unremovableOf(FlussConnector.class);
    }

    @BuildStep
    RuntimeInitializedClassBuildItem runtimeInitMetadataUtils() {
        return new RuntimeInitializedClassBuildItem("org.apache.fluss.client.utils.MetadataUtils");
    }

    @BuildStep
    ReflectiveClassBuildItem registerReflection() {
        return ReflectiveClassBuildItem.builder(
                "org.apache.fluss.row.GenericRow",
                "org.apache.fluss.row.InternalRow",
                "org.apache.fluss.config.Configuration",
                "org.apache.fluss.client.ConnectionFactory",
                "org.apache.fluss.metadata.TablePath",
                "org.apache.fluss.metadata.TableBucket")
                .methods(true)
                .fields(true)
                .build();
    }
}
