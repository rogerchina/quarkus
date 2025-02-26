package io.quarkus.vertx.http.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.steps.NativeBuild;
import io.quarkus.runtime.util.ClassPathUtils;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;
import io.quarkus.vertx.http.runtime.HttpConfiguration;
import io.quarkus.vertx.http.runtime.StaticResourcesRecorder;

/**
 * Handles all static file resources found in {@code META-INF/resources} unless the servlet container is present.
 */
public class StaticResourcesProcessor {

    @Deprecated
    public static final class StaticResourcesBuildItem extends SimpleBuildItem {

        private final Set<Entry> entries;

        public StaticResourcesBuildItem(Set<Entry> entries) {
            this.entries = entries;
        }

        public Set<Entry> getEntries() {
            return entries;
        }

        public Set<String> getPaths() {
            Set<String> paths = new HashSet<>(entries.size());
            for (Entry entry : entries) {
                paths.add(entry.getPath());
            }
            return paths;
        }

        public static class Entry {
            private final String path;
            private final boolean isDirectory;

            public Entry(String path, boolean isDirectory) {
                this.path = path;
                this.isDirectory = isDirectory;
            }

            public String getPath() {
                return path;
            }

            public boolean isDirectory() {
                return isDirectory;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o)
                    return true;
                if (o == null || getClass() != o.getClass())
                    return false;
                Entry entry = (Entry) o;
                return isDirectory == entry.isDirectory && path.equals(entry.path);
            }

            @Override
            public int hashCode() {
                return Objects.hash(path, isDirectory);
            }
        }

    }

    @BuildStep
    void collectStaticResources(Capabilities capabilities, ApplicationArchivesBuildItem applicationArchivesBuildItem,
            List<AdditionalStaticResourceBuildItem> additionalStaticResources,
            Optional<StaticResourcesBuildItem> deprecatedStaticResources,
            BuildProducer<io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem> staticResources) throws Exception {
        if (capabilities.isPresent(Capability.SERVLET)) {
            // Servlet container handles static resources
            return;
        }
        // Copy deprecated build item
        Set<io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry> paths = getClasspathResources(
                applicationArchivesBuildItem);
        if (deprecatedStaticResources.isPresent()) {
            Set<StaticResourcesBuildItem.Entry> deprecatedEntries = deprecatedStaticResources.get().getEntries();
            for (StaticResourcesBuildItem.Entry deprecatedEntry : deprecatedEntries) {
                paths.add(new io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry(deprecatedEntry.getPath(),
                        deprecatedEntry.isDirectory()));
            }
        }
        for (AdditionalStaticResourceBuildItem bi : additionalStaticResources) {
            paths.add(new io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry(bi.getPath(), bi.isDirectory()));
        }
        if (!paths.isEmpty()) {
            staticResources.produce(new io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem(paths));
        }
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    public void runtimeInit(Optional<io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem> staticResources,
            StaticResourcesRecorder recorder, CoreVertxBuildItem vertx, BeanContainerBuildItem beanContainer,
            BuildProducer<DefaultRouteBuildItem> defaultRoutes, HttpConfiguration config) {
        if (staticResources.isPresent()) {
            defaultRoutes.produce(new DefaultRouteBuildItem(recorder.start(staticResources.get().getPaths())));
        }
    }

    @BuildStep(onlyIf = NativeBuild.class)
    public void nativeImageResource(Optional<io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem> staticResources,
            BuildProducer<NativeImageResourceBuildItem> producer) {
        if (staticResources.isPresent()) {
            Set<io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry> entries = staticResources.get()
                    .getEntries();
            List<String> metaInfResources = new ArrayList<>(entries.size());
            for (io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry entry : entries) {
                if (entry.isDirectory()) {
                    // TODO: do we perhaps want to register the whole directory?
                    continue;
                }
                String metaInfResourcesPath = StaticResourcesRecorder.META_INF_RESOURCES + entry.getPath();
                metaInfResources.add(metaInfResourcesPath);
            }
            producer.produce(new NativeImageResourceBuildItem(metaInfResources));
        }
    }

    /**
     * Find all static file resources that are available from classpath.
     *
     * @param applicationArchivesBuildItem
     * @return the set of static resources
     * @throws Exception
     */
    private Set<io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry> getClasspathResources(
            ApplicationArchivesBuildItem applicationArchivesBuildItem)
            throws Exception {
        Set<io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry> knownPaths = new HashSet<>();

        for (ApplicationArchive i : applicationArchivesBuildItem.getAllApplicationArchives()) {
            i.accept(tree -> {
                Path resource = tree.getPath(StaticResourcesRecorder.META_INF_RESOURCES);
                if (resource != null && Files.exists(resource)) {
                    collectKnownPaths(resource, knownPaths);
                }
            });
        }

        ClassPathUtils.consumeAsPaths(StaticResourcesRecorder.META_INF_RESOURCES, resource -> {
            collectKnownPaths(resource, knownPaths);
        });

        return knownPaths;
    }

    private void collectKnownPaths(Path resource,
            Set<io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry> knownPaths) {
        try {
            Files.walkFileTree(resource, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path p, BasicFileAttributes attrs)
                        throws IOException {
                    String simpleName = p.getFileName().toString();
                    String file = resource.relativize(p).toString();
                    if (simpleName.equals("index.html") || simpleName.equals("index.htm")) {
                        Path parent = resource.relativize(p).getParent();
                        if (parent == null) {
                            knownPaths.add(new io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry("/", true));
                        } else {
                            String parentString = parent.toString();
                            if (!parentString.startsWith("/")) {
                                parentString = "/" + parentString;
                            }
                            knownPaths.add(new io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry(
                                    parentString + "/", true));
                        }
                    }
                    if (!file.startsWith("/")) {
                        file = "/" + file;
                    }
                    // Windows has a backslash
                    file = file.replace('\\', '/');
                    knownPaths.add(new io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem.Entry(file, false));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
