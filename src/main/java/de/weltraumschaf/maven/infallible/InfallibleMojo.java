package de.weltraumschaf.maven.infallible;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.maven.plugin.AbstractMojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

/**
 * This mojo searches for files with language code to test against parser generated by given grammar.
 * <p>
 * Example configuration:
 * </p>
 * <pre>{@code
 *  <plugin>
 *      <groupId>de.weltraumschaf.maven</groupId>
 *      <artifactId>antlr4grammartest-maven-plugin</artifactId>
 *      <version>1.0.0</version>
 *
 *      <configuration>
 *          <skip>false</skip>
 *          <startRule>startRule</startRule>
 *          <grammarName>Snafu</grammarName>
 *          <packageName>foo.bar.baz</packageName>
 *
 *          <filesets>
 *              <fileset>
 *                  <directory>some/relative/path</directory>
 *
 *                  <includes>
 *                      <include>**&#47;*.snf</include>
 *                  </includes>
 *
 *                  <excludes>
 *                      <exclude>**&#47;log.log</exclude>
 *                  </excludes>
 *              </fileset>
 *          </filesets>
 *      </configuration>
 *
 *      <executions>
 *          <execution>
 *              <id>parse-all-files</id>
 *
 *              <goals>
 *                  <goal>parse</goal>
 *              </goals>
 *          </execution>
 *      </executions>
 * </plugin>
 * }</pre>
 *
 * XXX Maybe LifecyclePhase.INTEGRATION_TEST or LifecyclePhase.VERIFY is better.
 *
 * XXX Make it thread safe?
 *
 * @since 1.0.0
 * @author Sven Strittmatter &lt;weltraumschaf@googlemail.com&gt;
 */
@Mojo(name = InfallibleMojo.GOAL, defaultPhase = LifecyclePhase.TEST, requiresProject = true, threadSafe = false)
public final class InfallibleMojo extends AbstractMojo {

    /**
     * The goal name for this plugin.
     */
    static final String GOAL = "parse";
    /**
     * Default encoding to read files.
     *
     * FIXME Use the Maven OM property for resource encoding.
     */
    static final String DEFAULT_ENCODING = "utf-8";

    /**
     * Whether the plugin execution should be skipped or not.
     */
    @Parameter(property = "infallible.skip")
    private boolean skip;
    /**
     * The name of the rule where to start the parsing.
     */
    @Parameter(required = true)
    private String startRule;
    /**
     * NAme of the parsed grammar.
     */
    @Parameter(required = true)
    private String grammarName;
    /**
     * Optional package name.
     */
    @Parameter
    private String packageName = "";
    /**
     * Encoding of the tested source files.
     */
    @Parameter(defaultValue = DEFAULT_ENCODING)
    private String encoding = DEFAULT_ENCODING;
    /**
     * Which files to test.
     *
     * https://maven.apache.org/shared/file-management/examples/mojo.html
     */
    @Parameter
    private FileSet[] filesets;
    /**
     * Needed for classloader.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File outputDirectory;

    private final FileSetManager fileSetManager = new FileSetManager();

    boolean isSkip() {
        return skip;
    }

    String getStartRule() {
        return startRule;
    }

    String getGrammarName() {
        return grammarName;
    }

    String getPackageName() {
        return packageName;
    }

    FileSet[] getFilesets() {
        return filesets;
    }

    String getEncoding() {
        return encoding;
    }

    Collection<String> getFilesToTest() {
        final Collection<String> aggregator = new ArrayList<>();

        for (final FileSet set : filesets) {
            for (final String file : fileSetManager.getIncludedFiles(set)) {
                aggregator.add(String.format("%s/%s", set.getDirectory(), file));
            }
        }

        return aggregator;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Execution skipped.");
            return;
        }

        printStartInfo();
        final Collector tested = parseFiles();
        getLog().info(new ResultFormatter().format(tested));

        if (tested.hasFailed()) {
            throw new MojoFailureException("TODO");
        }
    }

    void printStartInfo() {
        getLog().info("-------------------------------------------------------");
        getLog().info("ANTLR4 Grammar Test");
        getLog().info("-------------------------------------------------------");
    }

    private Collector parseFiles() throws MojoExecutionException {
        final ParserFactory parsers = new ParserFactory(
            getLog(),
            new ClassLoaderFactory(outputDirectory).getClassLoader(),
            packageName,
            grammarName);
        final Collector tested = new Collector();

        for (final String fileToTest : getFilesToTest()) {
            final Path absoluteFileName = Paths.get(fileToTest).toAbsolutePath();
            final Parser parser = parsers.create(absoluteFileName, encoding);
            getLog().info(String.format("Parse file '%s'...", absoluteFileName.toString()));
            final ParserInvoker invocation = new ParserInvoker(getLog(), parser, startRule);
            tested.add(invocation.invoke());
        }

        return tested;
    }

}
