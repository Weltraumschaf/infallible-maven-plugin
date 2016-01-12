package de.weltraumschaf.maven.infallible;

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
    static final String GOAL = "test";
    static final String DEFAULT_ENCODING = "utf-8";
    private final String NL = String.format("%n");

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

        getLog().info(formatStartInfo());
        final Collector tested = parseFiles();
        getLog().info(formatResult(tested));
    }

    String formatStartInfo() {
        return String.format(
            "-------------------------------------------------------%n"
            + "ANTLR4 Grammar Test%n"
            + "-------------------------------------------------------");
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

    String formatResult(final Collector tested) {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("Results:").append(NL).append(NL);

        if (tested.hasFailed()) {
            buffer.append("Failed sources:").append(NL);
            tested.results().stream().filter(r -> r.isFailed()).forEach(r -> {
                buffer.append("  ").append(r.getTestedFile()).append(NL);
                buffer.append("    ").append(r.getError().getMessage()).append(NL);
            });
            buffer.append(NL);
        }

        buffer.append(String.format("Sources parsed: %d, Failed: %d%n", tested.count(), tested.countFailed()));
        return buffer.toString();
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
            return getClass().getClassLoader().loadClass(name).asSubclass(superType);
        } catch (final ClassNotFoundException ex) {
            throw new MojoExecutionException(String.format("Can not create class '%s'!", name));
        }
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