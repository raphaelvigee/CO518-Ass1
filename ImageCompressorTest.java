import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class ImageCompressorTest
{
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][]{
                {"./test-images/test-image1", 14},
                {"./test-images/test-image2", 29},
                {"./test-images/test-image3", 200},
                {"./test-images/test-image4", 22},
                {"./test-images/test-image5", 26},
                {"./pixel-art/pixel-art1", 227},
                {"./pixel-art/pixel-art2", 189},
                {"./pixel-art/pixel-art3", 43},
                {"./pixel-art/pixel-art4", 60},
                {"./pixel-art/pixel-art5", 184},
                {"./pixel-art/pixel-art6", 115},
        });
    }

    @RunWith(Parameterized.class)
    public static class ParameterizedTests
    {
        @Parameter(0)
        public String filename;

        @Parameter(1)
        public Integer maxNumberCommands;

        @Parameters(name = "{0}")
        public static Collection<Object[]> data()
        {
            return ImageCompressorTest.data();
        }

        @Test
        public void testWithFile()
        {
            Image i = new Image(filename);

            Drawing d = i.compress();

            int n = d.commands.size();

            System.out.println("Nb commands: " + n);

            if (null != maxNumberCommands) {
                assertTrue("commands number lower or equal than " + maxNumberCommands + ", got " + n, n <= maxNumberCommands);
            }

            try {
                assertEquals(i.toString(), d.draw().toString());
            } catch (BadCommand e) {
                fail(e.toString());
            }
        }
    }

    public static class SingleTests
    {
        @Test
        public void score()
        {
            int score = 0;

            for (Object[] f : ImageCompressorTest.data()) {
                Image i = new Image((String) f[0]);
                Drawing d = i.compress();
                score += d.commands.size();
            }

            System.out.println(score);
        }
    }
}
