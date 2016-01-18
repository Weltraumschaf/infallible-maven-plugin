package de.weltraumschaf.maven.infallible;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ParserInvoker}.
 *
 * @author Sven Strittmatter &lt;weltraumschaf@googlemail.com&gt;
 */
public class ParserInvokerTest {

    private static final String SOURCE_NAME = "foobar.snf";

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void invoke() throws MojoExecutionException, Throwable {
        final ParserStub parser = spy(new ParserStub());
        final ParserInvoker sut = new ParserInvoker(mock(Log.class), parser, "foobar");

        final Result result = sut.invoke();

        verify(parser, times(1)).foobar();
        assertThat(result.isFailed(), is(false));
        assertThat(result.getError(), is(nullValue()));
        assertThat(result.getTestedFile(), is(SOURCE_NAME));
    }

    @Test
    public void invoke_throwsParseCancellationException() throws MojoExecutionException, Throwable {
        final ParserInvoker sut = new ParserInvoker(mock(Log.class), new ParserStubWithParseError(), "foobar");

        final Result result = sut.invoke();

        assertThat(result.isFailed(), is(true));
        assertThat(result.getError(), is(instanceOf(ParseCancellationException.class)));
        assertThat(result.getError().getMessage(), is("snafu"));
        assertThat(result.getTestedFile(), is(SOURCE_NAME));
    }

    @Test
    public void invoke_throwsIllegalAccessException() throws MojoExecutionException, Throwable {
        final ParserInvoker sut = new ParserInvoker(mock(Log.class), new ParserStubWithMethodIsNotPublic(), "foobar");

        thrown.expect(MojoExecutionException.class);
        thrown.expectMessage(
            "Can't access method 'foobar' on parser (Class de.weltraumschaf.maven.infallible.ParserInvoker can not "
                + "access a member of class de.weltraumschaf.maven.infallible.ParserInvokerTest$"
                + "ParserStubWithMethodIsNotPublic with modifiers \"private\")!");

        sut.invoke();
    }

    @Test
    public void invoke_throwsNoSuchMethodException() throws MojoExecutionException, Throwable {
        final ParserInvoker sut = new ParserInvoker(mock(Log.class), new ParserStubWithMethodNotExists(), "foobar");

        thrown.expect(MojoExecutionException.class);
        thrown.expectMessage(
            "Given parser has no method with name 'foobar' (de.weltraumschaf.maven.infallible."
                + "ParserInvokerTest$ParserStubWithMethodNotExists.foobar())");

        sut.invoke();
    }

    public static abstract class AbstractParserStub extends Parser {

        public AbstractParserStub() {
            super(mock(TokenStream.class));
        }

        @Override
        public String getSourceName() {
            return SOURCE_NAME;
        }

        @Override
        public String[] getTokenNames() {
            return new String[0];
        }

        @Override
        public String[] getRuleNames() {
            return new String[0];
        }

        @Override
        public String getGrammarFileName() {
            return "";
        }

        @Override
        public ATN getATN() {
            return null;
        }

    }

    public static class ParserStub extends AbstractParserStub {

        /**
         * Called by subject under test.
         */
        public void foobar() throws Throwable {
        }
    }

    public static class ParserStubWithParseError extends AbstractParserStub {

        /**
         * Called by subject under test.
         */
        public void foobar() throws Throwable {
            throw new ParseCancellationException("snafu");
        }
    }

    public static class ParserStubWithMethodNotExists extends AbstractParserStub {
    }

    public static class ParserStubWithMethodIsNotPublic extends AbstractParserStub {

        /**
         * Called by subject under test.
         */
        private void foobar() {
        }
    }
}
