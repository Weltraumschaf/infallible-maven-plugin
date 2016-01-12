package de.weltraumschaf.maven.infallible;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;
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
        final Constructor<? extends Lexer> lexerConstructor = createLexerConstructor();
        final Constructor<? extends Parser> parserConstructor = createParserConstructor();
        final Collector tested = new Collector();

        for (final String fileToTest : getFilesToTest()) {
            tested.add(parseFile(fileToTest, lexerConstructor, parserConstructor));
        }

        return tested;
    }

    final Constructor<? extends Lexer> createLexerConstructor() throws MojoExecutionException {
        final String lexerClassName = generateClassName(packageName, grammarName, "Lexer");
        getLog().info(String.format("Using lexer class '%s'.", lexerClassName));
        final Class<? extends Lexer> lexerClass = createClass(lexerClassName, Lexer.class);

        try {
            return lexerClass.getConstructor(CharStream.class);
        } catch (final NoSuchMethodException | SecurityException ex) {
            throw new MojoExecutionException(String.format("Can not get constructor for '%s'!", lexerClassName));
        }
    }

    final Constructor<? extends Parser> createParserConstructor() throws MojoExecutionException {
        final String parserClassName = generateClassName(packageName, grammarName, "Parser");
        getLog().info(String.format("Using parser class '%s'.", parserClassName));
        final Class<? extends Parser> parserClass = createClass(parserClassName, Parser.class);

        try {
            return parserClass.getConstructor(TokenStream.class);
        } catch (final NoSuchMethodException | SecurityException ex) {
            throw new MojoExecutionException(String.format("Can not get constructor for '%s'!", parserClassName));
        }
    }

    private <U> Class<? extends U> createClass(final String name, final Class<U> superType) throws MojoExecutionException {
        try {
            return getClassLoader().loadClass(name).asSubclass(superType);
        } catch (final ClassNotFoundException | MalformedURLException ex) {
            throw new MojoExecutionException(
                String.format("Can not create class '%s' (%s)!", name, ex.getMessage()), ex);
        }
    }

    private ClassLoader getClassLoader() throws MalformedURLException {
        return new ClassLoaderFactory(outputDirectory).getClassLoader();
    }

    static String generateClassName(final String packageName, final String grammarName, final String suffix) {
        final StringBuilder buffer = new StringBuilder();

        if (!packageName.isEmpty()) {
            buffer.append(packageName).append('.');
        }

        buffer.append(grammarName).append(suffix);
        return buffer.toString();
    }

    private Result parseFile(final String fileToTest, final Constructor<?> lexerConstructor, final Constructor<?> parserConstructor) throws MojoExecutionException {
        final Path absoluteFileName = Paths.get(fileToTest).toAbsolutePath();
        getLog().info(String.format("Parse file '%s'...", absoluteFileName.toString()));

        try {
            final ANTLRFileStream stream = new ANTLRFileStream(absoluteFileName.toString(), encoding);
            final Lexer lexer = (Lexer) lexerConstructor.newInstance(stream);
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final Parser parser = (Parser) parserConstructor.newInstance(tokens);
            parser.setErrorHandler(new BailErrorStrategy());
            final Method start = parser.getClass().getMethod(startRule);
            start.invoke(parser);
            return Result.passed(fileToTest);
        } catch (final IOException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        } catch (final ParseCancellationException ex) {
            getLog().error(ex.getMessage(), ex);
            return Result.failed(fileToTest, ex);
        }
    }

}
