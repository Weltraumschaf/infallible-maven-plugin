package de.weltraumschaf.maven.infallible;

import java.io.File;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.shared.model.fileset.FileSet;
import static org.codehaus.plexus.PlexusTestCase.getTestFile;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

/**
 * Tests for {@link InfallibleMojo}.
 *
 * @author Sven Strittmatter &lt;weltraumschaf@googlemail.com&gt;
 */
public final class InfallibleMojoTest extends AbstractMojoTestCase {

    private static final String FIXTURE_POM = "src/test/resources/fixture-pom.xml";
    private InfallibleMojo sut;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final File pom = getTestFile(FIXTURE_POM);
        assertThat(pom, is(not(nullValue())));
        assertThat(pom.exists(), is(true));
        sut = (InfallibleMojo) lookupMojo(InfallibleMojo.GOAL, pom);
        assertThat(sut, is(not(nullValue())));
    }

    @Test
    public void testConfigurationFromPom() {
        assertThat(sut.isSkip(), is(false));
        assertThat(sut.getStartRule(), is("startRule"));
        assertThat(sut.getGrammarName(), is("Snafu"));
        assertThat(sut.getPackageName(), is("foo.bar.baz"));
        assertThat(sut.getEncoding(), is(InfallibleMojo.DEFAULT_ENCODING));
        assertThat(sut.getFilesets(), is(not(nullValue())));
        assertThat(sut.getFilesets().length, is(1));

        final FileSet fileset = sut.getFilesets()[0];
        assertThat(fileset.getDirectory(), is("src/test/snafu"));
        assertThat(fileset.getIncludes(), hasSize(1));
        assertThat(fileset.getIncludes(), contains("**/*.snf"));
        assertThat(fileset.getExcludes(), hasSize(1));
        assertThat(fileset.getExcludes(), contains("**/*.log"));
    }

    
    @Test
    public void testGetFilesToTest() {
        assertThat(sut.getFilesToTest(), hasSize(3));
        assertThat(
            sut.getFilesToTest(),
            containsInAnyOrder(
                "src/test/snafu/some.snf",
                "src/test/snafu/with_errors.snf",
                "src/test/snafu/without_errors.snf"));
    }

    @Test
    public void testPrintStartInfo() throws MojoExecutionException, MojoFailureException {
        final Log log = mock(Log.class);
        sut.setLog(log);

        sut.printStartInfo();

        final InOrder order = inOrder(log);
        order.verify(log, times(1)).info("-------------------------------------------------------");
        order.verify(log, times(1)).info("ANTLR4 Grammar Test");
        order.verify(log, times(1)).info("-------------------------------------------------------");
    }

}
