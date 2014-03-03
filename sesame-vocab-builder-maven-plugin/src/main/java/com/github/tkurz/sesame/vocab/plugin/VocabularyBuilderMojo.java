package com.github.tkurz.sesame.vocab.plugin;

import com.github.tkurz.sesame.vocab.GenerationException;
import com.github.tkurz.sesame.vocab.VocabBuilder;
import com.github.tkurz.sesame.vocab.plugin.Vocabulary;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.openrdf.model.util.GraphUtilException;
import org.openrdf.rio.RDFParseException;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven Plugin to generate Sesame Vocabulary Classes.
 * @author Jakob Frank (jakob@apache.org)
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class VocabularyBuilderMojo extends AbstractMojo {

    @Parameter(property = "output", defaultValue = "${project.build.directory}/generated-sources/sesame-vocabs", readonly = true)
    private File outputDirectory;

    @Parameter
    private List<Vocabulary> vocabularies;

    @Parameter(property = "url")
    private URL url;
    @Parameter(property = "file")
    private File file;
    @Parameter(property = "name")
    private String name;
    @Parameter(property = "className")
    private String className;

    @Parameter(readonly = true, property = "package")
    private String packageName;

    @Parameter(readonly = true, alias = "format")
    private String mimeType;

    @Component
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        StaticLoggerBinder.getSingleton().setLog(getLog());
        try {
            final Path output = outputDirectory.toPath();

            if (vocabularies == null) {
                vocabularies = new ArrayList<>();
            }

            if (url != null) {
                vocabularies.add(0, new Vocabulary(url, name, className));
            } else if (file != null) {
                vocabularies.add(0, new Vocabulary(file, name, className));
            }


            Files.createDirectories(output);


            final Log log = getLog();
            log.info(String.format("Generating %d vocabularies", vocabularies.size()));

            for (Vocabulary vocab : vocabularies) {
                final String displayName = vocab.getName() != null?vocab.getName():vocab.getClassName();
                if (displayName == null) {
                    log.error("Incomplete Configuration: Vocabulary without className or name");
                    throw new MojoExecutionException("Incomplete Configuration: Vocabulary without className or name");
                }
                try {
                    final String mime;
                    if (vocab.getMimeType() != null) {
                        mime = vocab.getMimeType();
                    } else {
                        mime = mimeType;
                    }

                    final VocabBuilder builder;
                    if (vocab.getUrl() != null) {
                        log.warn("Generating from URL is not implemented!");
                        continue;
                    } else if (vocab.getFile() != null) {
                        builder = new VocabBuilder(vocab.getFile().getAbsolutePath(), mime);

                    } else {
                        final String msg = String.format("Incomplete Configuration for %s: Vocabulary without URL or FILE param!", displayName);
                        log.error(msg);
                        throw new MojoExecutionException(msg);
                    }

                    builder.setPackageName(packageName);
                    if (vocab.getMimeType() != null) {
                        builder.setPackageName(vocab.getPackageName());
                    }

                    builder.setName(vocab.getName());

                    String fName;
                    if (vocab.getClassName() != null) {
                        fName = vocab.getClassName() + ".java";
                    } else if (vocab.getName() != null) {
                        fName = StringUtils.capitalize(vocab.getName()) + ".java";
                    } else {
                        throw new MojoExecutionException("Incomplete Configuration: Vocabulary without className or name");
                    }

                    Path target = output;
                    if (builder.getPackageName() != null) {
                        target = target.resolve(builder.getPackageName().replaceAll("\\.", "/"));
                        Files.createDirectories(target);
                    }

                    final Path vFile = target.resolve(fName);
                    builder.run(vFile);
                    log.info(String.format("Generated %s: %s", displayName, vFile));

                } catch (RDFParseException e) {
                    throw new MojoFailureException(String.format("Could not parse vocabulary %s: %s", displayName, e.getMessage()));
                } catch (GraphUtilException e) {
                    throw new MojoExecutionException("Internal Mojo Error", e);
                } catch (GenerationException e) {
                    throw new MojoFailureException(String.format("Could not generate vocabulary %s: %s", displayName, e.getMessage()));
                }
            }
            log.info("Vocabulary generation complete");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not write Vocabularies", e);
        }
    }
}
